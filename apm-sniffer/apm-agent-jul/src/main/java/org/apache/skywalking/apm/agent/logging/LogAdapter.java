package org.apache.skywalking.apm.agent.logging;

import java.io.Serializable;
import java.util.logging.LogRecord;

final class LogAdapter {

    static Log createLog(String name) {
        return JavaUtilAdapter.createLog(name);
    }

    private static class JavaUtilAdapter {

        static Log createLog(String name) {
            return new JavaUtilLog(name);
        }
    }

    private static class JavaUtilLog implements Log, Serializable {

        private String name;

        private transient java.util.logging.Logger logger;

        JavaUtilLog(String name) {
            this.name = name;
            this.logger = java.util.logging.Logger.getLogger(name);
        }

        public boolean isFatalEnabled() {
            return isErrorEnabled();
        }

        public boolean isErrorEnabled() {
            return this.logger.isLoggable(java.util.logging.Level.SEVERE);
        }

        public boolean isWarnEnabled() {
            return this.logger.isLoggable(java.util.logging.Level.WARNING);
        }

        public boolean isInfoEnabled() {
            return this.logger.isLoggable(java.util.logging.Level.INFO);
        }

        public boolean isDebugEnabled() {
            return this.logger.isLoggable(java.util.logging.Level.FINE);
        }

        public boolean isTraceEnabled() {
            return this.logger.isLoggable(java.util.logging.Level.FINEST);
        }

        public void fatal(Object message) {
            error(message);
        }

        public void fatal(Object message, Throwable exception) {
            error(message, exception);
        }

        public void error(Object message) {
            log(java.util.logging.Level.SEVERE, message, null);
        }

        public void error(Object message, Throwable exception) {
            log(java.util.logging.Level.SEVERE, message, exception);
        }

        public void warn(Object message) {
            log(java.util.logging.Level.WARNING, message, null);
        }

        public void warn(Object message, Throwable exception) {
            log(java.util.logging.Level.WARNING, message, exception);
        }

        public void info(Object message) {
            log(java.util.logging.Level.INFO, message, null);
        }

        public void info(Object message, Throwable exception) {
            log(java.util.logging.Level.INFO, message, exception);
        }

        public void debug(Object message) {
            log(java.util.logging.Level.FINE, message, null);
        }

        public void debug(Object message, Throwable exception) {
            log(java.util.logging.Level.FINE, message, exception);
        }

        public void trace(Object message) {
            log(java.util.logging.Level.FINEST, message, null);
        }

        public void trace(Object message, Throwable exception) {
            log(java.util.logging.Level.FINEST, message, exception);
        }

        private void log(java.util.logging.Level level, Object message, Throwable exception) {
            if (this.logger.isLoggable(level)) {
                LogRecord rec;
                if (message instanceof LogRecord) {
                    rec = (LogRecord) message;
                } else {
                    rec = new LocationResolvingLogRecord(level, String.valueOf(message));
                    rec.setLoggerName(this.name);
                    rec.setResourceBundleName(logger.getResourceBundleName());
                    rec.setResourceBundle(logger.getResourceBundle());
                    rec.setThrown(exception);
                }
                logger.log(rec);
            }
        }

        protected Object readResolve() {
            return new JavaUtilLog(this.name);
        }
    }

    @SuppressWarnings("serial")
    private static class LocationResolvingLogRecord extends LogRecord {

        private static final String FQCN = JavaUtilLog.class.getName();

        private volatile boolean resolved;

        LocationResolvingLogRecord(java.util.logging.Level level, String msg) {
            super(level, msg);
        }

        @Override
        public String getSourceClassName() {
            if (!this.resolved) {
                resolve();
            }
            return super.getSourceClassName();
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            super.setSourceClassName(sourceClassName);
            this.resolved = true;
        }

        @Override
        public String getSourceMethodName() {
            if (!this.resolved) {
                resolve();
            }
            return super.getSourceMethodName();
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            super.setSourceMethodName(sourceMethodName);
            this.resolved = true;
        }

        private void resolve() {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            String sourceClassName = null;
            String sourceMethodName = null;
            boolean found = false;
            for (StackTraceElement element : stack) {
                String className = element.getClassName();
                if (FQCN.equals(className)) {
                    found = true;
                } else if (found) {
                    sourceClassName = className;
                    sourceMethodName = element.getMethodName();
                    break;
                }
            }
            setSourceClassName(sourceClassName);
            setSourceMethodName(sourceMethodName);
        }

        protected Object writeReplace() {
            LogRecord serialized = new LogRecord(getLevel(), getMessage());
            serialized.setLoggerName(getLoggerName());
            serialized.setResourceBundle(getResourceBundle());
            serialized.setResourceBundleName(getResourceBundleName());
            serialized.setSourceClassName(getSourceClassName());
            serialized.setSourceMethodName(getSourceMethodName());
            serialized.setSequenceNumber(getSequenceNumber());
            serialized.setParameters(getParameters());
            serialized.setThreadID(getThreadID());
            serialized.setMillis(getMillis());
            serialized.setThrown(getThrown());
            return serialized;
        }
    }

}
