package androidx.fragment.app;
import android.content.Context; import android.os.Bundle; import android.view.*;
public class Fragment implements androidx.lifecycle.LifecycleOwner {
  public View onCreateView(LayoutInflater i, ViewGroup c, Bundle s){return null;}
  public void onViewCreated(View v, Bundle s){} public void onResume(){}
  public final Context requireContext(){return null;} public final boolean isAdded(){return false;} public View getView(){return null;}
  public void startActivity(android.content.Intent i){} public final String getString(int id){return null;} }
