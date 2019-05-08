package de.streblow.markmycar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OpenMapDialog {
    private String m_sdcardDirectory = "";
    private Context m_context;
    private TextView m_titleView;
    public String Default_File_Name = "";
    private String Selected_File_Name = Default_File_Name;

    private String m_dir = "";
    private List<String> m_subdirs = null;
    private OpenMapDialogListener m_OpenMapDialogListener = null;
    private AlertDialog m_dirsDialog = null;
    private ArrayAdapter<String> m_listAdapter = null;

    static ContextThemeWrapper themeWrapper = null;

    public interface OpenMapDialogListener
    {
        public void onChosenDir(String chosenDir);
    }

    public OpenMapDialog(Context context, OpenMapDialogListener OpenMapDialogListener)
    {
        m_context = context;
        m_OpenMapDialogListener = OpenMapDialogListener;
        m_sdcardDirectory = new File("/").getAbsolutePath();
    }

    public void chooseFile()
    {
        if (m_dir.equals(""))
            chooseFile(m_sdcardDirectory);
        else
            chooseFile(m_dir);
    }

    public void chooseFile(String dir)
    {
        class OpenMapDialogOnClickListener implements DialogInterface.OnClickListener
        {
            public void onClick(DialogInterface dialog, int item)
            {
                String m_dir_old = m_dir;
                String sel = "" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
                if (sel.charAt(sel.length() - 1) == '/')
                    sel = sel.substring(0, sel.length() - 1);
                if (sel.equals(".."))
                {
                    m_dir = new File(m_dir).getParentFile().getAbsolutePath();
                }
                else
                {
                    if (m_dir.equalsIgnoreCase("/"))
                        m_dir += sel;
                    else
                        m_dir += "/" + sel;
                }
                Selected_File_Name = Default_File_Name;
                if ((new File(m_dir).isFile()))
                {
                    m_dir = m_dir_old;
                    Selected_File_Name = sel;
                    String filepath = m_dir + "/" + Selected_File_Name;
                    filepath = new File(filepath).getAbsolutePath();
                    m_OpenMapDialogListener.onChosenDir(filepath);
                    m_dirsDialog.dismiss();

                } else
                    updateDirectory();
            }
        }

        File dirFile = new File(dir);
        if (! dirFile.exists() || ! dirFile.isDirectory())
        {
            dir = m_sdcardDirectory;
        }

        dir = new File(dir).getAbsolutePath();
        m_dir = dir;
        m_subdirs = getDirectories(dir);
        AlertDialog.Builder dialogBuilder = createDirectoryChooserDialog(dir, m_subdirs,
                new OpenMapDialogOnClickListener());
        m_dirsDialog = dialogBuilder.create();
        m_dirsDialog.show();
    }

    private List<String> getDirectories(String dir)
    {
        List<String> dirs = new ArrayList<String>();
        List<String> files = new ArrayList<String>();
        try
        {
            File dirFile = new File(dir);
            if (dirFile.getParentFile() != null)
                dirs.add("..");
            if (!dirFile.exists() || !dirFile.isDirectory())
                return dirs;
            if (dir.equalsIgnoreCase("/")) {
                dirs.add("mnt/");
                dirs.add("sdcard/");
                dirs.add("storage/");
                return dirs;
            }
            for (File file : dirFile.listFiles())
            {
                if (file.isDirectory())
                    // Add "/" to directory names to identify them in the list
                    dirs.add(file.getName() + "/");
                else
                    // Add file names to the list if we are doing a file save or file open operation
                    files.add(file.getName());
            }
        }
        catch (Exception e)	{
            Log.d("MarkMyCar", e.getMessage());
        }
        Collections.sort(dirs, new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareTo(o2);
            }
        });
        Collections.sort(files, new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareTo(o2);
            }
        });
        dirs.addAll(files);
        return dirs;
    }

    private AlertDialog.Builder createDirectoryChooserDialog(String title, List<String> listItems,
                                                             DialogInterface.OnClickListener onClickListener)
    {
        themeWrapper = new ContextThemeWrapper(m_context, R.style.AppTheme);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(themeWrapper);
        m_titleView = new TextView(m_context);
        m_titleView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        m_titleView.setText(m_context.getResources().getText(R.string.open_map_dialog));
        m_titleView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleLayout = new LinearLayout(m_context);
        titleLayout.setOrientation(LinearLayout.VERTICAL);
        titleLayout.addView(m_titleView);
        dialogBuilder.setCustomTitle(titleLayout);
        m_listAdapter = createListAdapter(listItems);
        dialogBuilder.setSingleChoiceItems(m_listAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(true);
        return dialogBuilder;
    }

    private void updateDirectory()
    {
        m_subdirs.clear();
        m_subdirs.addAll(getDirectories(m_dir));
        if (Selected_File_Name.isEmpty())
            m_dirsDialog.getListView().smoothScrollToPosition(0);
        m_listAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<String> createListAdapter(List<String> items)
    {
        return new ArrayAdapter<String>(m_context, android.R.layout.select_dialog_item, android.R.id.text1, items)
        {
            @Override
            public View getView(int position, View convertView, ViewGroup parent)
            {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView)
                {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }
}
