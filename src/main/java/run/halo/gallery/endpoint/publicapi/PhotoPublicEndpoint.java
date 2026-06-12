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
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.photo.Photo;

@Component
@RequiredArgsConstructor
public class PhotoPublicEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "PhotoPublicV1alpha1";
        return route()
            .GET("/photos", this::listPhotos,
                builder -> builder.operationId("publicListPhotos").tag(tag)
                    .description("公开获取照片列表"))
            .GET("/photos/{name}", this::getPhoto,
                builder -> builder.operationId("publicGetPhoto").tag(tag)
                    .description("公开获取照片"))
            .build();
    }

    private Mono<ServerResponse> listPhotos(ServerRequest request) {
        var albumName = request.queryParam("albumName").orElse(null);
        var page = Integer.parseInt(request.queryParam("page").orElse("1"));
        var size = Integer.parseInt(request.queryParam("size").orElse("20"));

        var builder = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"));
        if (albumName != null && !albumName.isBlank()) {
            builder.andQuery(QueryFactory.equal("spec.albumName", albumName));
        }
        var options = builder.build();

        return client.listBy(Photo.class, options,
                PageRequestImpl.of(page, size))
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getPhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Photo.class, name)
            .flatMap(photo -> ServerResponse.ok().bodyValue(photo))
            .switchIfEmpty(Mono.error(new RuntimeException("照片不存在")));
    }
}
