/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.ListFragment;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.Vector;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.activities.FileSelect;

public class FileSelectionFragment extends ListFragment {

    private static final String ITEM_KEY = "key";
    private static final String ITEM_IMAGE = "image";
    private static final String ROOT = "/";


    private List<String> path = null;
    private TextView myPath;
    private ArrayList<HashMap<String, Object>> mList;

    private Button selectButton;


    private String parentPath;
    private String currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();


    private String[] formatFilter = null;

    private File selectedFile;
    private HashMap<String, Integer> lastPositions = new HashMap<>();
    private String mStartPath;
    private CheckBox mInlineImport;
    private Button mClearButton;
    private boolean mHideImport = false;


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                                                     @Override
                                                     public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                                                         onListItemClick(getListView(), view, position, id);
                                                         onFileSelectionClick();
                                                         return true;
                                                     }
                                                 }

        );

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.file_dialog_main, container, false);

        myPath = (TextView) v.findViewById(R.id.path);

        mInlineImport = (CheckBox) v.findViewById(R.id.doinline);

        if (mHideImport) {
            mInlineImport.setVisibility(View.GONE);
            mInlineImport.setChecked(false);
        }


        selectButton = (Button) v.findViewById(R.id.fdButtonSelect);
        selectButton.setEnabled(false);
        selectButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                onFileSelectionClick();
            }
        });

        mClearButton = (Button) v.findViewById(R.id.fdClear);
        mClearButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                ((FileSelect) getActivity()).clearData();
            }
        });
        if (!((FileSelect) getActivity()).showClear()) {
            mClearButton.setVisibility(View.GONE);
            mClearButton.setEnabled(false);
        }

        return v;
    }

    private void onFileSelectionClick() {
        if (selectedFile != null) {
            if (mInlineImport.isChecked())

                ((FileSelect) getActivity()).importFile(selectedFile.getPath());
            else
                ((FileSelect) getActivity()).setFile(selectedFile.getPath());
        }
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mStartPath = ((FileSelect) getActivity()).getSelectPath();
        getDir(mStartPath);
    }

    public void refresh() {
        getDir(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    private void getDir(String dirPath) {

        boolean useAutoSelection = dirPath.length() < currentPath.length();

        Integer position = lastPositions.get(parentPath);

        getDirImpl(dirPath);

        if (position != null && useAutoSelection) {
            getListView().setSelection(position);
        }

    }

    private void getDirImpl(final String dirPath) {

        currentPath = dirPath;

        final List<String> item = new ArrayList<String>();
        path = new ArrayList<String>();
        mList = new ArrayList<HashMap<String, Object>>();

        File f = new File(currentPath);
        File[] files = f.listFiles();
        if (files == null) {
            currentPath = ROOT;
            f = new File(currentPath);
            files = f.listFiles();

            if (files == null)
                files = new File[]{};
        }

        myPath.setText(getText(R.string.location) + ": " + currentPath);

        if (!currentPath.equals(ROOT)) {

            item.add(ROOT);
            addItem(ROOT, R.drawable.ic_root_folder_am);
            path.add(ROOT);

            item.add("../");
            addItem("../", R.drawable.ic_root_folder_am);
            path.add(f.getParent());
            parentPath = f.getParent();

        }


        TreeMap<String, String> dirsMap = new TreeMap<>();
        TreeMap<String, String> dirsPathMap = new TreeMap<>();
        TreeMap<String, String> filesMap = new TreeMap<>();
        TreeMap<String, String> filesPathMap = new TreeMap<>();

        // add default locations
        for (String dir: getExternalStorages()) {
            // You got to love the P8 Lite to have null in this list ....
            if (dir!=null) {
                dirsMap.put(dir, dir);
                dirsPathMap.put(dir, dir);
            }
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                dirsMap.put(dirName, dirName);
                dirsPathMap.put(dirName, file.getPath());
            } else {
                final String fileName = file.getName();
                final String fileNameLwr = fileName.toLowerCase(Locale.getDefault());

                if (formatFilter != null) {
                    boolean contains = false;
                    for (String aFormatFilter : formatFilter) {
                        final String formatLwr = aFormatFilter.toLowerCase(Locale.getDefault());
                        if (fileNameLwr.endsWith(formatLwr)) {
                            contains = true;
                            break;
                        }
                    }
                    if (contains) {
                        filesMap.put(fileName, fileName);
                        filesPathMap.put(fileName, file.getPath());
                    }
                } else {
                    filesMap.put(fileName, fileName);
                    filesPathMap.put(fileName, file.getPath());
                }
            }
        }
        item.addAll(dirsMap.tailMap("").values());
        item.addAll(filesMap.tailMap("").values());
        path.addAll(dirsPathMap.tailMap("").values());
        path.addAll(filesPathMap.tailMap("").values());

        SimpleAdapter fileList = new SimpleAdapter(getActivity(), mList, R.layout.file_dialog_row, new String[]{
                ITEM_KEY, ITEM_IMAGE}, new int[]{R.id.fdrowtext, R.id.fdrowimage});

        for (String dir : dirsMap.tailMap("").values()) {
            addItem(dir, R.drawable.ic_root_folder_am);
        }

        for (String file : filesMap.tailMap("").values()) {
            addItem(file, R.drawable.ic_doc_generic_am);
        }

        fileList.notifyDataSetChanged();

        setListAdapter(fileList);

    }

    private void addItem(String fileName, int imageId) {
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put(ITEM_KEY, fileName);
        item.put(ITEM_IMAGE, imageId);
        mList.add(item);
    }

    private Collection<String> getExternalStorages() {
        Vector<String> dirs = new Vector<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (File d : getActivity().getExternalFilesDirs(null))
                dirs.add(getRootOfInnerSdCardFolder(d));
        } else {
            dirs.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        }
        return dirs;
    }

    private static String getRootOfInnerSdCardFolder(File file) {
        if (file == null)
            return null;
        final long totalSpace = file.getTotalSpace();
        while (true) {
            final File parentFile = file.getParentFile();
            if (parentFile == null || parentFile.getTotalSpace() != totalSpace
                    || file.equals(Environment.getExternalStorageDirectory()))
                return file.getAbsolutePath();
            file = parentFile;
        }
    }



    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

        File file = new File(path.get(position));

        if (file.isDirectory()) {
            selectButton.setEnabled(false);

            if (file.canRead()) {
                lastPositions.put(currentPath, position);
                getDir(path.get(position));
            } else {
                Toast.makeText(getActivity(),
                        "[" + file.getName() + "] " + getActivity().getText(R.string.cant_read_folder),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            selectedFile = file;
            v.setSelected(true);
            selectButton.setEnabled(true);
        }
    }

    public void setNoInLine() {
        mHideImport = true;

    }

}
