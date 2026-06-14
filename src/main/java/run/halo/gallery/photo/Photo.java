package run.halo.gallery.photo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "gallery.halo.run", version = "v1alpha1",
    kind = "Photo", plural = "photos", singular = "photo")
public class Photo extends AbstractExtension {

    public static final String KIND = "Photo";

    @Schema(requiredMode = REQUIRED)
    private PhotoSpec spec;

    private PhotoStatus status;

    @Data
    public static class PhotoSpec {
        private String title;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private String albumName;

        @Schema(requiredMode = REQUIRED)
        private String url;

        private String thumbnail;

        private Integer priority = 0;

        private Boolean visible = true;
    }

    @Data
    public static class PhotoStatus {
    }
}
