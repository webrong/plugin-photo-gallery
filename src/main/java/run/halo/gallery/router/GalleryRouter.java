package run.halo.gallery.router;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.UrlContextListResult;
import run.halo.gallery.finder.GalleryFinder;
import run.halo.gallery.vo.PhotoVo;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@RequiredArgsConstructor
public class GalleryRouter {

    private final TemplateNameResolver templateNameResolver;
    private final GalleryFinder galleryFinder;

    @Bean
    RouterFunction<ServerResponse> galleryRouterFunction() {
        return route(GET("/gallery"), this::renderAlbumList)
            .andRoute(GET("/gallery/page/{page}"), this::renderAlbumList)
            .andRoute(GET("/gallery/{slug}"), this::renderAlbumDetail)
            .andRoute(GET("/gallery/{slug}/page/{page}"), this::renderAlbumDetail);
    }

    Mono<ServerResponse> renderAlbumList(ServerRequest request) {
        return galleryFinder.listAlbums()
            .collectList()
            .flatMap(albums -> {
                Map<String, Object> model = new HashMap<>();
                model.put("albums", albums);
                model.put("title", "相册");
                return renderTemplate(request, "gallery", model);
            });
    }

    Mono<ServerResponse> renderAlbumDetail(ServerRequest request) {
        String slug = request.pathVariable("slug");
        int page = positiveInt(request.queryParam("page").orElse(null),
            pathVariableAsInt(request, "page", 1));
        int size = 20;

        return galleryFinder.getAlbum(slug)
            .flatMap(album -> galleryFinder.listPhotos(slug, page, size)
                .flatMap(photos -> {
                    Map<String, Object> model = new HashMap<>();
                    model.put("album", album);
                    model.put("photos", buildUrlContext(photos, slug, page, size));
                    model.put("title", album.getSpec().getDisplayName() + " - 相册");
                    return renderTemplate(request, "gallery_detail", model);
                })
            )
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private UrlContextListResult<PhotoVo> buildUrlContext(
        run.halo.app.extension.ListResult<PhotoVo> result,
        String slug, int page, int size) {
        String baseUrl = "/gallery/" + slug;
        return new UrlContextListResult.Builder<PhotoVo>()
            .listResult(result)
            .nextUrl(result.hasNext() ? baseUrl + "/page/" + (page + 1) : null)
            .prevUrl(result.hasPrevious() ? baseUrl + "/page/" + (page - 1) : null)
            .build();
    }

    private Mono<ServerResponse> renderTemplate(ServerRequest request,
        String templateName, Map<String, Object> model) {
        return templateNameResolver.resolveTemplateNameOrDefault(
                request.exchange(), templateName)
            .flatMap(name -> ServerResponse.ok().render(name, model));
    }

    private static int positiveInt(String value, int defaultValue) {
        try {
            int v = Integer.parseInt(value);
            return v > 0 ? v : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int pathVariableAsInt(ServerRequest request, String name, int defaultValue) {
        try {
            return positiveInt(request.pathVariable(name), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
