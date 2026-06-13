package run.halo.gallery.endpoint;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.app.extension.router.SortableRequest;
import run.halo.gallery.photo.Photo;
import run.halo.gallery.util.UrlSanitizer;

@Component
@RequiredArgsConstructor
public class PhotoEndpoint implements CustomEndpoint {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

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
        var page = positiveInt(request.queryParam("page").orElse(null), 1);
        var size = capSize(positiveInt(request.queryParam("size").orElse(null), DEFAULT_SIZE));

        var builder = ListOptions.builder();
        if (albumName != null && !albumName.isBlank()) {
            builder.fieldQuery(QueryFactory.equal("spec.albumName", albumName));
        }
        var options = builder.build();

        return client.listBy(Photo.class, options, PageRequestImpl.of(page, size))
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getPhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Photo.class, name)
            .flatMap(photo -> ServerResponse.ok().bodyValue(photo))
            .switchIfEmpty(Mono.error(notFound("照片不存在")));
    }

    private Mono<ServerResponse> createPhoto(ServerRequest request) {
        return request.bodyToMono(Photo.class)
            .switchIfEmpty(Mono.error(badRequest("请求体不能为空")))
            .flatMap(photo -> validatePhotoSpec(photo)
                .then(client.create(applyDefaults(photo)))
                .flatMap(saved -> ServerResponse.ok().bodyValue(saved)));
    }

    private Mono<ServerResponse> updatePhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(Photo.class)
            .switchIfEmpty(Mono.error(badRequest("请求体不能为空")))
            .flatMap(photo -> client.fetch(Photo.class, name)
                .switchIfEmpty(Mono.error(notFound("照片不存在")))
                .flatMap(existing -> {
                    var spec = photo.getSpec();
                    if (spec == null) {
                        return Mono.error(badRequest("spec 不能为空"));
                    }
                    if (spec.getAlbumName() == null || spec.getAlbumName().isBlank()) {
                        spec.setAlbumName(existing.getSpec().getAlbumName());
                    }
                    if (spec.getUrl() == null || spec.getUrl().isBlank()) {
                        spec.setUrl(existing.getSpec().getUrl());
                    }
                    if (spec.getVisible() == null) {
                        spec.setVisible(existing.getSpec().getVisible());
                    }
                    if (spec.getPriority() == null) {
                        spec.setPriority(existing.getSpec().getPriority());
                    }
                    applyDefaults(photo);
                    existing.setSpec(spec);
                    return client.update(existing);
                }))
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> deletePhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Photo.class, name)
            .switchIfEmpty(Mono.error(notFound("照片不存在")))
            .flatMap(client::delete)
            .then(ServerResponse.ok().build());
    }

    private static Mono<Void> validatePhotoSpec(Photo photo) {
        var spec = photo.getSpec();
        if (spec == null) {
            return Mono.error(badRequest("spec 不能为空"));
        }
        if (spec.getAlbumName() == null || spec.getAlbumName().isBlank()) {
            return Mono.error(badRequest("albumName 不能为空"));
        }
        if (spec.getUrl() == null || spec.getUrl().isBlank()) {
            return Mono.error(badRequest("url 不能为空"));
        }
        return Mono.empty();
    }

    private static Photo applyDefaults(Photo photo) {
        var spec = photo.getSpec();
        if (spec.getPriority() == null) {
            spec.setPriority(0);
        }
        if (spec.getVisible() == null) {
            spec.setVisible(true);
        }
        spec.setUrl(UrlSanitizer.sanitize(spec.getUrl()));
        if (spec.getThumbnail() != null) {
            spec.setThumbnail(UrlSanitizer.sanitize(spec.getThumbnail()));
        }
        return photo;
    }

    private static int positiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(value);
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "page/size 必须是正整数");
        }
    }

    private static int capSize(int size) {
        return Math.min(size, MAX_SIZE);
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
