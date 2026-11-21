package androidx.recyclerview.widget;
import android.view.*;
public class RecyclerView extends ViewGroup { public RecyclerView(android.content.Context c){super(c);} protected void onLayout(boolean a,int b,int c,int d,int e){}
  public void setLayoutManager(LayoutManager l){} public void setAdapter(Adapter<?> a){} public void addOnScrollListener(OnScrollListener l){}
  public abstract static class LayoutManager {}
  public abstract static class OnScrollListener { public void onScrolled(RecyclerView rv, int dx, int dy){} }
  public abstract static class ViewHolder { public final View itemView; public ViewHolder(View v){ itemView = v; } }
  public abstract static class Adapter<VH extends ViewHolder> { public abstract VH onCreateViewHolder(ViewGroup p, int t); public abstract void onBindViewHolder(VH h, int pos);
    public abstract int getItemCount(); public final void notifyDataSetChanged(){} } }
