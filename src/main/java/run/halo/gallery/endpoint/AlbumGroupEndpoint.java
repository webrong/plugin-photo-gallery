package run.halo.gallery.endpoint;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.app.extension.router.SortableRequest;
import run.halo.gallery.group.AlbumGroup;
import run.halo.gallery.album.Album;

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
            .flatMap(listResult -> {
                List<AlbumGroup> groups = new ArrayList<>();
                for (var item : listResult.getItems()) {
                    groups.add((AlbumGroup) item);
                }
                return enrichGroupsWithAlbumCount(groups)
                    .collectList()
                    .map(enriched -> new ListResult<>(
                        listResult.getPage(), listResult.getSize(),
                        listResult.getTotal(), enriched));
            })
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .flatMap(group -> countAlbumsInGroup(name)
                .map(count -> {
                    if (group.getStatus() == null) {
                        group.setStatus(new AlbumGroup.AlbumGroupStatus());
                    }
                    group.getStatus().setAlbumCount(count);
                    return group;
                })
            )
            .flatMap(group -> ServerResponse.ok().bodyValue(group))
            .switchIfEmpty(Mono.error(new RuntimeException("分组不存在")));
    }

    private Mono<ServerResponse> createGroup(ServerRequest request) {
        return request.bodyToMono(AlbumGroup.class)
            .flatMap(group -> client.create(group))
            .flatMap(group -> ServerResponse.ok().bodyValue(group));
    }

    private Mono<ServerResponse> updateGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(AlbumGroup.class)
            .flatMap(group -> client.fetch(AlbumGroup.class, name)
                .flatMap(existing -> {
                    existing.setSpec(group.getSpec());
                    return client.update(existing);
                }))
            .flatMap(group -> ServerResponse.ok().bodyValue(group));
    }

    private Mono<ServerResponse> deleteGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .flatMap(client::delete)
            .then(ServerResponse.ok().build());
    }

    private reactor.core.publisher.Flux<AlbumGroup> enrichGroupsWithAlbumCount(
        List<AlbumGroup> groups) {
        return reactor.core.publisher.Flux.fromIterable(groups)
            .flatMap(group -> countAlbumsInGroup(group.getMetadata().getName())
                .map(count -> {
                    if (group.getStatus() == null) {
                        group.setStatus(new AlbumGroup.AlbumGroupStatus());
                    }
                    group.getStatus().setAlbumCount(count);
                    return group;
                })
                .defaultIfEmpty(group)
            );
    }

    private Mono<Integer> countAlbumsInGroup(String groupName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.groupName", groupName))
            .build();
        return client.countBy(Album.class, options).map(Long::intValue);
    }
}
