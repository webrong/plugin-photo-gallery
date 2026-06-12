package run.halo.gallery.endpoint;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.album.Album;

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
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Album.class, name)
            .flatMap(album -> ServerResponse.ok().bodyValue(album))
            .switchIfEmpty(Mono.error(new RuntimeException("相册不存在")));
    }

    private Mono<ServerResponse> createAlbum(ServerRequest request) {
        return request.bodyToMono(Album.class)
            .flatMap(album -> client.create(album))
            .flatMap(album -> ServerResponse.ok().bodyValue(album));
    }

    private Mono<ServerResponse> updateAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(Album.class)
            .flatMap(album -> client.fetch(Album.class, name)
                .flatMap(existing -> {
                    existing.setSpec(album.getSpec());
                    return client.update(existing);
                }))
            .flatMap(album -> ServerResponse.ok().bodyValue(album));
    }

    private Mono<ServerResponse> deleteAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Album.class, name)
            .flatMap(client::delete)
            .then(ServerResponse.ok().build());
    }
}
