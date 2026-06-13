package run.halo.gallery.router;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.app.plugin.SettingFetcher;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.UrlContextListResult;
import run.halo.gallery.finder.GalleryFinder;
import run.halo.gallery.vo.PhotoVo;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@RequiredArgsConstructor
public class GalleryRouter {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int DEFAULT_PHOTO_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_TITLE = "相册";
    private static final String RESERVED_SLUG = "page";

    private final TemplateNameResolver templateNameResolver;
    private final GalleryFinder galleryFinder;
    private final SettingFetcher settingFetcher;

    @Bean
    RouterFunction<ServerResponse> galleryRouterFunction() {
        return route(GET("/gallery/page/{page}"), this::renderAlbumList)
            .andRoute(GET("/gallery/{slug}/page/{page}"), this::renderAlbumDetail)
            .andRoute(GET("/gallery/{slug}"), this::renderAlbumDetail)
            .andRoute(GET("/gallery"), this::renderAlbumList);
    }

    Mono<ServerResponse> renderAlbumList(ServerRequest request) {
        Integer pageVar = pathVariableOrNull(request, "page");
        int page = positiveInt(request.queryParam("page").orElse(null),
            pageVar != null ? pageVar : 1);

        if (pageVar != null && page == 1) {
            return ServerResponse.permanentRedirect(
                    java.net.URI.create("/gallery"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
        }

        return getPageSize()
            .map(GalleryRouter::capSize)
            .flatMap(size -> galleryFinder.listAlbumsPaged(page, size)
                .flatMap(albums -> getTitle()
                    .flatMap(title -> {
                        Map<String, Object> model = new HashMap<>();
                        model.put("albums", albums);
                        model.put("title", title);
                        return renderTemplate(request, "gallery", model);
                    })))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    Mono<ServerResponse> renderAlbumDetail(ServerRequest request) {
        String slug = request.pathVariable("slug");
        if (RESERVED_SLUG.equalsIgnoreCase(slug)) {
            return ServerResponse.notFound().build();
        }
        Integer pageVar = pathVariableOrNull(request, "page");
        int page = positiveInt(request.queryParam("page").orElse(null),
            pageVar != null ? pageVar : 1);

        if (pageVar != null && page == 1) {
            return ServerResponse.permanentRedirect(
                    java.net.URI.create("/gallery/" + slug))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
        }

        return getPhotoPageSize()
            .map(GalleryRouter::capSize)
            .flatMap(size -> galleryFinder.getAlbum(slug)
                .flatMap(album -> galleryFinder.listPhotos(slug, page, size)
                    .flatMap(photos -> {
                        Map<String, Object> model = new HashMap<>();
                        model.put("album", album);
                        model.put("photos", buildUrlContext(photos, slug, page, size));
                        return getTitle()
                            .map(title -> {
                                model.put("title", album.getSpec().getDisplayName() + " - " + title);
                                return model;
                            })
                            .flatMap(m -> renderTemplate(request, "gallery_detail", m));
                    })))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private UrlContextListResult<PhotoVo> buildUrlContext(
        ListResult<PhotoVo> result, String slug, int page, int size) {
        String baseUrl = "/gallery/" + slug;
        String prevUrl = null;
        if (result.hasPrevious()) {
            prevUrl = page > 2 ? baseUrl + "/page/" + (page - 1) : baseUrl;
        }
        return new UrlContextListResult.Builder<PhotoVo>()
            .listResult(result)
            .nextUrl(result.hasNext() ? baseUrl + "/page/" + (page + 1) : null)
            .prevUrl(prevUrl)
            .build();
    }

    private Mono<ServerResponse> renderTemplate(ServerRequest request,
        String templateName, Map<String, Object> model) {
        return templateNameResolver.resolveTemplateNameOrDefault(
                request.exchange(), templateName)
            .flatMap(name -> ServerResponse.ok().render(name, model));
    }

    private Mono<String> getTitle() {
        return Mono.fromCallable(() -> settingFetcher.getSettingValue("base"))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .map(node -> node.path("title").asText(""))
            .filter(v -> v != null && !v.isBlank())
            .defaultIfEmpty(DEFAULT_TITLE);
    }

    private Mono<Integer> getPageSize() {
        return Mono.fromCallable(() -> parseIntSetting("base", "pageSize", DEFAULT_PAGE_SIZE))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<Integer> getPhotoPageSize() {
        return Mono.fromCallable(() -> parseIntSetting("base", "photoPageSize", DEFAULT_PHOTO_PAGE_SIZE))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private int parseIntSetting(String group, String key, int defaultValue) {
        try {
            var node = settingFetcher.getSettingValue(group).path(key);
            if (node.isMissingNode() || node.isNull()) {
                return defaultValue;
            }
            return node.asInt(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
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

    private static Integer pathVariableOrNull(ServerRequest request, String name) {
        try {
            return Integer.parseInt(request.pathVariable(name));
        } catch (Exception e) {
            return null;
        }
    }

    private static int capSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
