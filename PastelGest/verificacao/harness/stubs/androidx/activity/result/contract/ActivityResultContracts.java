package androidx.activity.result.contract;
import android.net.Uri; import android.content.Intent; import androidx.activity.result.ActivityResult;
public final class ActivityResultContracts {
  public static class GetContent extends ActivityResultContract<String, Uri> {}
  public static class CreateDocument extends ActivityResultContract<String, Uri> { public CreateDocument(String mime){} }
  public static class OpenDocument extends ActivityResultContract<String[], Uri> {}
  public static final class StartActivityForResult extends ActivityResultContract<Intent, ActivityResult> {}
}
