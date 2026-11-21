package androidx.appcompat.app;
import android.content.DialogInterface; import android.widget.Button;
public class AlertDialog extends android.app.Dialog implements DialogInterface { public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
  protected AlertDialog(android.content.Context c){super(c);} public Button getButton(int w){return null;} }
