package run.halo.gallery.endpoint;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.album.Album;
import run.halo.gallery.util.UrlSanitizer;
import run.halo.gallery.photo.Photo;

@Component
@RequiredArgsConstructor
public class AlbumEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "AlbumV1alpha1";
        return route()
            .GET("/albums", this::listAlbums,
                builder -> builder.operationId("listAlbums").tag(tag).description("列出相册"))
            .GET("/albums/{name}", this::getAlbum,
                builder -> builder.operationId("getAlbum").tag(tag).description("获取相册"))
            .POST("/albums", this::createAlbum,
                builder -> builder.operationId("createAlbum").tag(tag).description("创建相册"))
            .PUT("/albums/{name}", this::updateAlbum,
                builder -> builder.operationId("updateAlbum").tag(tag).description("更新相册"))
            .DELETE("/albums/{name}", this::deleteAlbum,
                builder -> builder.operationId("deleteAlbum").tag(tag).description("删除相册"))
            .build();
    }

    private Mono<ServerResponse> listAlbums(ServerRequest request) {
        var sortReq = new SortableRequest(request.exchange());
        return client.listBy(Album.class, sortReq.toListOptions(), sortReq.toPageRequest())
            .flatMap(listResult -> Flux.fromIterable(listResult.getItems())
                .flatMap(this::attachPhotoCount, 8)
                .collectList()
                .map(enriched -> new ListResult<>(
                    listResult.getPage(), listResult.getSize(),
                    listResult.getTotal(), enriched)))
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Album.class, name)
            .switchIfEmpty(Mono.error(EndpointUtils.notFound("相册不存在")))
            .flatMap(this::attachPhotoCount)
            .flatMap(album -> ServerResponse.ok().bodyValue(album));
    }

    private Mono<Album> attachPhotoCount(Album album) {
        return countPhotos(album.getMetadata().getName())
            .map(count -> {
                if (album.getStatus() == null) {
                    album.setStatus(new Album.AlbumStatus());
                }
                album.getStatus().setPhotoCount(count);
                return album;
            });
    }

    private Mono<Integer> countPhotos(String albumName) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.albumName", albumName))
            .build();
        return client.countBy(Photo.class, options).map(Long::intValue);
    }

    private Mono<ServerResponse> createAlbum(ServerRequest request) {
        return request.bodyToMono(Album.class)
            .switchIfEmpty(Mono.error(EndpointUtils.badRequest("请求体不能为空")))
            .flatMap(album -> validateAlbumSpec(album)
                .then(client.create(applyDefaults(album)))
                .flatMap(saved -> ServerResponse.ok().bodyValue(saved)));
    }

    private Mono<ServerResponse> updateAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(Album.class)
            .switchIfEmpty(Mono.error(EndpointUtils.badRequest("请求体不能为空")))
            .flatMap(album -> client.fetch(Album.class, name)
                .switchIfEmpty(Mono.error(EndpointUtils.notFound("相册不存在")))
                .flatMap(existing -> {
                    var spec = album.getSpec();
                    if (spec == null) {
                        return Mono.error(EndpointUtils.badRequest("spec 不能为空"));
                    }
                    if (spec.getSlug() == null || spec.getSlug().isBlank()) {
                        spec.setSlug(existing.getSpec().getSlug());
                    }
                    if (spec.getDisplayName() == null || spec.getDisplayName().isBlank()) {
                        spec.setDisplayName(existing.getSpec().getDisplayName());
                    }
                    existing.setSpec(spec);
                    return client.update(existing);
                }))
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> deleteAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Album.class, name)
            .switchIfEmpty(Mono.error(EndpointUtils.notFound("相册不存在")))
            .flatMap(client::delete)
            .then(ServerResponse.ok().build());
        // Photos are cascade-deleted by AlbumReconciler via finalizer
    }

    private static Mono<Void> validateAlbumSpec(Album album) {
        if (album.getSpec() == null) {
            return Mono.error(EndpointUtils.badRequest("spec 不能为空"));
        }
        if (album.getSpec().getDisplayName() == null || album.getSpec().getDisplayName().isBlank()) {
            return Mono.error(EndpointUtils.badRequest("displayName 不能为空"));
        }
        if (album.getSpec().getSlug() == null || album.getSpec().getSlug().isBlank()) {
            return Mono.error(EndpointUtils.badRequest("slug 不能为空"));
        }
        return Mono.empty();
    }

    private static Album applyDefaults(Album album) {
        var spec = album.getSpec();
        if (spec.getPriority() == null) {
            spec.setPriority(0);
        }
        if (spec.getVisible() == null) {
            spec.setVisible(true);
        }
        spec.setCover(UrlSanitizer.sanitize(spec.getCover()));
        return album;
    }

}
