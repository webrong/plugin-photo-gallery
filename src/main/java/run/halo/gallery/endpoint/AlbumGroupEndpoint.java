package run.halo.gallery.endpoint;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.app.extension.router.SortableRequest;
import run.halo.gallery.album.Album;
import run.halo.gallery.group.AlbumGroup;
import run.halo.gallery.util.UrlSanitizer;

@Component
@RequiredArgsConstructor
public class AlbumGroupEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "AlbumGroupV1alpha1";
        return route()
            .GET("/albumgroups", this::listGroups,
                builder -> builder.operationId("listAlbumGroups").tag(tag).description("列出相册分组"))
            .GET("/albumgroups/{name}", this::getGroup,
                builder -> builder.operationId("getAlbumGroup").tag(tag).description("获取相册分组"))
            .POST("/albumgroups", this::createGroup,
                builder -> builder.operationId("createAlbumGroup").tag(tag).description("创建相册分组"))
            .PUT("/albumgroups/{name}", this::updateGroup,
                builder -> builder.operationId("updateAlbumGroup").tag(tag).description("更新相册分组"))
            .DELETE("/albumgroups/{name}", this::deleteGroup,
                builder -> builder.operationId("deleteAlbumGroup").tag(tag).description("删除相册分组"))
            .build();
    }

    private Mono<ServerResponse> listGroups(ServerRequest request) {
        var sortReq = new SortableRequest(request.exchange());
        return client.listBy(AlbumGroup.class, sortReq.toListOptions(), sortReq.toPageRequest())
            .flatMap(listResult -> enrichGroupsWithAlbumCount(listResult.getItems())
                .collectList()
                .map(enriched -> new ListResult<>(
                    listResult.getPage(), listResult.getSize(),
                    listResult.getTotal(), enriched)))
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .switchIfEmpty(Mono.error(notFound("分组不存在")))
            .flatMap(group -> countAlbumsInGroup(name)
                .map(count -> {
                    if (group.getStatus() == null) {
                        group.setStatus(new AlbumGroup.AlbumGroupStatus());
                    }
                    group.getStatus().setAlbumCount(count);
                    return group;
                }))
            .flatMap(group -> ServerResponse.ok().bodyValue(group));
    }

    private Mono<ServerResponse> createGroup(ServerRequest request) {
        return request.bodyToMono(AlbumGroup.class)
            .switchIfEmpty(Mono.error(badRequest("请求体不能为空")))
            .flatMap(group -> validateGroupSpec(group)
                .then(validateParentChain(null, group.getSpec().getParentName()))
                .then(client.create(applyGroupDefaults(group)))
                .flatMap(saved -> syncParentChildren(saved, group.getSpec().getParentName(), null)
                    .thenReturn(saved)))
            .flatMap(group -> ServerResponse.ok().bodyValue(group));
    }

    private Mono<ServerResponse> updateGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(AlbumGroup.class)
            .switchIfEmpty(Mono.error(badRequest("请求体不能为空")))
            .flatMap(group -> client.fetch(AlbumGroup.class, name)
                .switchIfEmpty(Mono.error(notFound("分组不存在")))
                .flatMap(existing -> {
                    var spec = group.getSpec();
                    if (spec == null) {
                        return Mono.error(badRequest("spec 不能为空"));
                    }
                    String oldParentName = existing.getSpec().getParentName();
                    String newParentName = spec.getParentName();
                    if (spec.getSlug() == null || spec.getSlug().isBlank()) {
                        spec.setSlug(existing.getSpec().getSlug());
                    }
                    if (spec.getDisplayName() == null || spec.getDisplayName().isBlank()) {
                        spec.setDisplayName(existing.getSpec().getDisplayName());
                    }
                    return validateParentChain(name, newParentName)
                        .then(Mono.defer(() -> {
                            existing.setSpec(spec);
                            return client.update(existing);
                        }))
                        .flatMap(updated -> syncParentChildren(updated, newParentName, oldParentName)
                            .thenReturn(updated));
                }))
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> deleteGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .switchIfEmpty(Mono.error(notFound("分组不存在")))
            .flatMap(group -> removeFromParent(group)
                .then(client.delete(group))
                .then(ServerResponse.ok().build()));
    }

    private Flux<AlbumGroup> enrichGroupsWithAlbumCount(List<AlbumGroup> groups) {
        return Flux.fromIterable(groups)
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> {
                    if (group.getStatus() == null) {
                        group.setStatus(new AlbumGroup.AlbumGroupStatus());
                    }
                    group.getStatus().setAlbumCount(count);
                    return group;
                }), 8);
    }

    private Mono<Integer> countAlbumsInGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .build();
        return client.countBy(Album.class, options).map(Long::intValue);
    }

    /**
     * Maintain bidirectional parent/children relationship atomically.
     * - For create: newParentName set, oldParentName null.
     * - For update: remove from old parent's children, add to new parent's children.
     */
    private Mono<Void> syncParentChildren(AlbumGroup group, String newParentName, String oldParentName) {
        String selfName = group.getMetadata().getName();
        Mono<Void> removeFromOld = (oldParentName != null && !oldParentName.isBlank()
            && !oldParentName.equals(newParentName))
            ? updateParentChildren(oldParentName, list -> {
                list.remove(selfName);
                return list;
            })
            : Mono.empty();
        Mono<Void> addToNew = (newParentName != null && !newParentName.isBlank()
            && !newParentName.equals(oldParentName))
            ? updateParentChildren(newParentName, list -> {
                if (!list.contains(selfName)) {
                    list.add(selfName);
                }
                return list;
            })
            : Mono.empty();
        return removeFromOld.then(addToNew);
    }

    private Mono<Void> removeFromParent(AlbumGroup group) {
        String parentName = Optional.ofNullable(group.getSpec())
            .map(AlbumGroup.AlbumGroupSpec::getParentName)
            .orElse(null);
        if (parentName == null || parentName.isBlank()) {
            return Mono.empty();
        }
        return updateParentChildren(parentName, list -> {
            list.remove(group.getMetadata().getName());
            return list;
        });
    }

    private Mono<Void> updateParentChildren(String parentName,
        java.util.function.Function<List<String>, List<String>> mutator) {
        return client.fetch(AlbumGroup.class, parentName)
            .flatMap(parent -> {
                List<String> children = new ArrayList<>(
                    Optional.ofNullable(parent.getSpec().getChildren())
                        .orElse(List.of()));
                children = mutator.apply(children);
                parent.getSpec().setChildren(children);
                return client.update(parent).then();
            })
            .retryWhen(Retry.backoff(3, java.time.Duration.ofMillis(20))
                .filter(OptimisticLockingFailureException.class::isInstance));
    }

    /**
     * Reject self-parenting and any cycle that would form if {@code name} is reparented
     * to {@code parentName}. Walks the parent chain once, depth bounded by the visited set.
     */
    private Mono<Void> validateParentChain(String name, String parentName) {
        if (parentName == null || parentName.isBlank()) {
            return Mono.empty();
        }
        if (name != null && parentName.equals(name)) {
            return Mono.error(badRequest("不能将自身设为父分组"));
        }
        Set<String> visited = new HashSet<>();
        if (name != null) {
            visited.add(name);
        }
        return walkParents(parentName, visited);
    }

    private Mono<Void> walkParents(String currentName, Set<String> visited) {
        if (!visited.add(currentName)) {
            return Mono.error(badRequest("父分组链存在循环引用"));
        }
        return client.fetch(AlbumGroup.class, currentName)
            .flatMap(group -> {
                String parent = Optional.ofNullable(group.getSpec())
                    .map(AlbumGroup.AlbumGroupSpec::getParentName)
                    .orElse(null);
                if (parent == null || parent.isBlank()) {
                    return Mono.empty();
                }
                return walkParents(parent, visited);
            })
            .switchIfEmpty(Mono.empty());
    }

    private static Mono<Void> validateGroupSpec(AlbumGroup group) {
        var spec = group.getSpec();
        if (spec == null) {
            return Mono.error(badRequest("spec 不能为空"));
        }
        if (spec.getDisplayName() == null || spec.getDisplayName().isBlank()) {
            return Mono.error(badRequest("displayName 不能为空"));
        }
        if (spec.getSlug() == null || spec.getSlug().isBlank()) {
            return Mono.error(badRequest("slug 不能为空"));
        }
        return Mono.empty();
    }

    private static AlbumGroup applyGroupDefaults(AlbumGroup group) {
        var spec = group.getSpec();
        if (spec.getPriority() == null) {
            spec.setPriority(0);
        }
        if (spec.getChildren() == null) {
            spec.setChildren(List.of());
        }
        spec.setCover(UrlSanitizer.sanitize(spec.getCover()));
        return group;
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
