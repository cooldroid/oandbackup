package dk.jens.backup;

import android.text.TextUtils;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandHandler {
    public int runCmd(String shell, List<String> commands,
            OutputConsumer outHandler, OutputConsumer errorHandler,
            ExceptionConsumer exceptionHandler, UnexpectedExceptionListener exceptionListener) {
        if(commands.size() == 0) {
            Log.w(OAndBackup.TAG, "no commands to run");
            errorHandler.accept("no commands to run");
            return 1;
        }

        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        // Run commands and get output immediately
        int code = 1;
        if (shell.equals("su")) {
            try {
                code = Shell.cmd(commands.toArray(new String[0])).to(stdout, stderr).exec().getCode();
            } catch (Exception e) {
                exceptionListener.onUnexpectedException(e);
            }
        } else {
            try {
                code = Shell.cmd(commands.toArray(new String[0])).to(stdout, stderr).exec().getCode();
            } catch (Exception e) {
                exceptionListener.onUnexpectedException(e);
            }
        }

        for (String line: stdout) {
            outHandler.accept(line);
        }

        for (String line: stderr) {
            errorHandler.accept(line);
        }

        if (!(code == 0)) {
            Exception t = new Exception(TextUtils.join("\n ", stderr));
            exceptionHandler.accept(t);
            code = 1;
        }

        return code;
    }

    public int runCmd(String shell, String command,
            OutputConsumer outputHandler, OutputConsumer errorHandler,
            ExceptionConsumer exceptionHandler, UnexpectedExceptionListener
            exceptionListener) {
        return runCmd(shell, Collections.singletonList(command),
            outputHandler, errorHandler, exceptionHandler, exceptionListener);
    }

    public interface OutputConsumer {
        void accept(String line);
    }

    public interface ExceptionConsumer {
        void accept(Throwable t);
    }

    public interface UnexpectedExceptionListener {
        void onUnexpectedException(Throwable t);
    }

}
