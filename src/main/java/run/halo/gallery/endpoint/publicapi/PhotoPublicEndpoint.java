package run.halo.gallery.endpoint.publicapi;

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
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.photo.Photo;
import run.halo.gallery.vo.PhotoVo;

@Component
@RequiredArgsConstructor
public class PhotoPublicEndpoint implements CustomEndpoint {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

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
        var page = positiveInt(request.queryParam("page").orElse(null), 1);
        var size = capSize(positiveInt(request.queryParam("size").orElse(null), DEFAULT_SIZE));

        var builder = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("spec.visible", "true"));
        if (albumName != null && !albumName.isBlank()) {
            builder.andQuery(QueryFactory.equal("spec.albumName", albumName));
        }
        var options = builder.build();

        return client.listBy(Photo.class, options, PageRequestImpl.of(page, size))
            .map(listResult -> {
                List<PhotoVo> vos = listResult.getItems().stream()
                    .map(PhotoVo::from).toList();
                return (ListResult<PhotoVo>) new ListResult<>(
                    listResult.getPage(), listResult.getSize(),
                    listResult.getTotal(), vos);
            })
            .flatMap(listResult -> ServerResponse.ok().bodyValue(listResult));
    }

    private Mono<ServerResponse> getPhoto(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(Photo.class, name)
            .filter(p -> Boolean.TRUE.equals(p.getSpec().getVisible()))
            .map(PhotoVo::from)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "照片不存在或不可见")));
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
}
