package dk.jens.backup;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileReaderWriter
{
    static final String TAG = OAndBackup.TAG;

    File file;
    public FileReaderWriter(String absolutePath)
    {
        this.file = new File(absolutePath);
    }
    public FileReaderWriter(String rootDirectoryPath, String name)
    {
        this.file = new File(rootDirectoryPath, name);
    }
    public boolean putString(String string, boolean append)
    {
        if(string != null && file != null) {
            try(FileWriter fw = new FileWriter(file.getAbsoluteFile(), append);
                    BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(string);
                if (append) {
                    bw.newLine();
                }
                return true;
            }
            catch(IOException e)
            {
                Log.i(TAG, e.toString());
            }
        }
        return false;
    }
    public String read()
    {
        try(FileReader fr = new FileReader(file); BufferedReader reader = new BufferedReader(fr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null)
            {
                sb.append(line).append("\n");
            }
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        catch(FileNotFoundException e)
        {
            return e.toString();
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return e.toString();
        }
    }
    public boolean contains(String string)
    {
        String[] lines = read().split("\n");
        for(String line : lines)
        {
            if(string.equals(line.trim()))
            {
                return true;
            }
        }
        return false;
    }
    public List<String> getList() {
        String[] lines = read().split("\n");
        return Arrays.asList(lines);
    }
    public void clear()
    {
        putString("", false);
    }
    public boolean rename(String newName)
    {
        if(file.exists())
        {
            File newFile = new File(file.getParent(), newName);
            boolean renamed = file.renameTo(newFile);
            if(renamed)
            {
                file = newFile;
            }
            return renamed;
        }
        return false;
    }
    public boolean delete()
    {
        return file.delete();
    }
}
