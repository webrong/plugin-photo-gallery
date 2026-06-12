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
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.photo.Photo;

@Component
@RequiredArgsConstructor
public class PhotoEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "PhotoV1alpha1";
        return route()
            .GET("/photos", this::listPhotos,
                builder -> builder.operationId("listPhotos").tag(tag).description("列出照片"))
            .GET("/photos/{name}", this::getPhoto,
                builder -> builder.operationId("getPhoto").tag(tag).description("获取照片"))
            .POST("/photos", this::createPhoto,
                builder -> builder.operationId("createPhoto").tag(tag).description("创建照片"))
            .PUT("/photos/{name}", this::updatePhoto,
                builder -> builder.operationId("updatePhoto").tag(tag).description("更新照片"))
            .DELETE("/photos/{name}", this::deletePhoto,
                builder -> builder.operationId("deletePhoto").tag(tag).description("删除照片"))
            .build();
    }

    private Mono<ServerResponse> listPhotos(ServerRequest request) {
        var albumName = request.queryParam("albumName").orElse(null);
        var page = Integer.parseInt(request.queryParam("page").orElse("1"));
        var size = Integer.parseInt(request.queryParam("size").orElse("20"));

        var builder = ListOptions.builder();
        if (albumName != null && !albumName.isBlank()) {
            builder.fieldQuery(QueryFactory.equal("spec.albumName", albumName));
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

    private Mono<ServerResponse> createPhoto(ServerRequest request) {
        return request.bodyToMono(Photo.class)
            .flatMap(photo -> client.create(photo))
            .flatMap(photo -> ServerResponse.ok().bodyValue(photo));
    }

    private Mono<ServerResponse> updatePhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(Photo.class)
            .flatMap(photo -> client.fetch(Photo.class, name)
                .flatMap(existing -> {
                    existing.setSpec(photo.getSpec());
                    return client.update(existing);
                }))
            .flatMap(photo -> ServerResponse.ok().bodyValue(photo));
    }

    private Mono<ServerResponse> deletePhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Photo.class, name)
            .flatMap(client::delete)
            .then(ServerResponse.ok().build());
    }
}
