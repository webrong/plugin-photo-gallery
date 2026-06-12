package run.halo.gallery.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "gallery.halo.run", version = "v1alpha1",
    kind = "Album", plural = "albums", singular = "album")
public class Album extends AbstractExtension {

    public static final String KIND = "Album";

    @Schema(requiredMode = REQUIRED)
    private AlbumSpec spec;

    private AlbumStatus status;

    @Data
    public static class AlbumSpec {
        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String displayName;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String slug;

        private String description;

        private String cover;

        @Schema(defaultValue = "0")
        private Integer priority;

        @Schema(defaultValue = "true")
        private Boolean visible;

        private String groupName;
    }

    @Data
    public static class AlbumStatus {
        private Integer photoCount;
        private String permalink;
    }
}
