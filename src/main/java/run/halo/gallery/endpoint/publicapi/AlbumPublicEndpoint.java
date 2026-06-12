package run.halo.gallery.endpoint.publicapi;

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
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.album.Album;

@Component
@RequiredArgsConstructor
public class AlbumPublicEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "AlbumPublicV1alpha1";
        return route()
            .GET("/albums", this::listAlbums,
                builder -> builder.operationId("publicListAlbums").tag(tag)
                    .description("公开获取相册列表"))
            .GET("/albums/{name}", this::getAlbum,
                builder -> builder.operationId("publicGetAlbum").tag(tag)
                    .description("公开获取相册"))
            .build();
    }

    private Mono<ServerResponse> listAlbums(ServerRequest request) {
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"))
            .build();
        return client.listAll(Album.class, options,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.asc("spec.priority")))
            .collectList()
            .flatMap(albums -> ServerResponse.ok().bodyValue(albums));
    }

    private Mono<ServerResponse> getAlbum(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Album.class, name)
            .flatMap(album -> ServerResponse.ok().bodyValue(album))
            .switchIfEmpty(Mono.error(new RuntimeException("相册不存在")));
    }
}
