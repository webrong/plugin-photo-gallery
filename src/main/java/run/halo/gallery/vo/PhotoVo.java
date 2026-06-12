package run.halo.gallery.vo;

import lombok.Builder;
import lombok.Data;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.gallery.photo.Photo;

@Data
@Builder
public class PhotoVo implements ExtensionVoOperator {

    private final MetadataOperator metadata;
    private final Photo.PhotoSpec spec;
    private final Photo.PhotoStatus status;

    public static PhotoVo from(Photo photo) {
        return PhotoVo.builder()
            .metadata(photo.getMetadata())
            .spec(photo.getSpec())
            .status(photo.getStatus())
            .build();
    }
}
