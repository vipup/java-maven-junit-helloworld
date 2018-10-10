package com.pacer.demo.junittests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cz.tto.te2.common.ILogger;
import cz.tto.te2.common.IPathResolver;
import cz.tto.te2.common.Issue;
import cz.tto.te2.common.LogLevelEnum;
import cz.tto.te2.compile.ICompiledUnit;
import cz.tto.te2.compile.IExecutionUnit;
import cz.tto.te2.execute.ExternalVariableTypeEnum;
import cz.tto.te2.functions.Functions;
import cz.tto.te2.types.io.IInputStreamProvider;
import cz.tto.te2.types.io.IOutputStreamProvider;
import cz.tto.te2jar.counter.CounterPreferences;
import cz.tto.te2jar.functions.CreateCounterFunction;
import cz.tto.te2jar.functions.CreateCounterWithParamsFunction;
import cz.tto.te2jar.functions.GetCounterFunction;
import cz.tto.te2jar.functions.IncrementCounterByValueFunction;
import cz.tto.te2jar.functions.IncrementCounterFunction;
import cz.tto.te2jar.functions.ResetCounterFunction;
import cz.tto.te2jar.jdbc.model.JDBCPreferences;
import cz.tto.te2jar.services.ServiceProvider;

//TODO 4 TSYS import cz.tto.te2jar.Executor;

class BasicTest {
	final String strFile = "test.TE2";
	LogLevelEnum logLevel = LogLevelEnum.DEBUG;
	final HashMap<String, String> variables = new HashMap<>();
	JDBCPreferences jdbcPref = null;
	CounterPreferences counterPref = null;
	private IExecutionUnit executable;
	private long lStartTime;
	private long lEndTime;

	@BeforeEach 
	void init() {

		// set counters implementations
		Functions.addBuiltIn(new IncrementCounterFunction());
		Functions.addBuiltIn(new IncrementCounterByValueFunction());
		Functions.addBuiltIn(new GetCounterFunction());
		Functions.addBuiltIn(new CreateCounterFunction());
		Functions.addBuiltIn(new CreateCounterWithParamsFunction());
		Functions.addBuiltIn(new ResetCounterFunction());

		BasicConfigurator.configure(new NullAppender());

		ICompiledUnit context = null;
		try {
			context = cz.tto.te2.compile.Compiler.compile(strFile, null, createPathResolver(), false, false);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
		executable = context.createExecutable();

		// set logger
		executable.setLogger(createLogger(logLevel));
		// set external variables
		setExtarnalVaribles(executable, variables);
		// set JDBC and counter services provider
		executable.setServiceProvider(new ServiceProvider(jdbcPref, counterPref));

		// check issues
		final List<Issue> issues = executable.getIssues();
		if (issues.size() > 0) {
			System.out.println("Errors:" + executable.getIssues());
			return;
		}

 
		// execution
		lStartTime = System.currentTimeMillis();
	}

	@AfterEach
	void stotexec() {
		lEndTime = System.currentTimeMillis();

		// print output
		printComletedMessage(lStartTime, lEndTime);
		printExtarnalVaribles(executable);

	}

	@Test
	void test1() {
		executable.execute();
	}

	/**
	 * @return Create path resolver.
	 */
	private static IPathResolver createPathResolver() {
		return new IPathResolver() {

			@Override
			public String getPath(final String strPath) {
				return getPath(strPath, null);
			}

			@Override
			public String getPath(final String strPath, final String strCurrentDir) {

				if (strCurrentDir != null && !strPath.startsWith("/") && !strPath.startsWith("file:")
						&& !strPath.startsWith("http:") && !strPath.startsWith("https:")) {
					return strCurrentDir + strPath;
				}

				if (strPath.startsWith("shared:")) {
					throw new RuntimeException("Path \"shared:\" is not supported.");
				}

				if (strPath.startsWith("http:") || strPath.startsWith("https:")) {
					return strPath;
				}

				if (strPath.startsWith("file:")) {
					try {
						return new URI(strPath).getPath();
					} catch (final URISyntaxException e) {
						throw new RuntimeException("Invalid syntax of URI: " + strPath, e);
					}
				}

				if (strPath.startsWith("/")) {
					final String strUserDir = System.getProperty("user.dir");
					return strUserDir + strPath;
				}

				return strPath;
			}
		};
	}

	/**
	 * Print message about "Completed successfully" and duration of execution.
	 * <p>
	 * The execution time is formated and in seconds.
	 * 
	 * @param startTime
	 *            start timestamp in milliseconds (new Date().getTime())
	 * @param endTime
	 *            end timestamp in milliseconds (new Date().getTime())
	 */
	private static void printComletedMessage(final Long startTime, final Long endTime) {

		final long executionTime = (endTime - startTime);

		final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
		symbols.setGroupingSeparator(' ');
		final DecimalFormat formatter = new DecimalFormat("###,##0.000", symbols);
		final String strTime = formatter.format(new Double(executionTime) / 1000);

		System.out.println();
		System.out.println("Completed successfully in " + strTime + " seconds.");
	}

	/**
	 * Get external variables form compiler. Text variables print into console and
	 * files variables write into files.
	 * 
	 * @param executable
	 *            execution unit
	 */
	private static void printExtarnalVaribles(final IExecutionUnit executable) {
		final String strNewLine = "\n";

		final StringBuilder message = new StringBuilder();

		for (final String strVariableName : executable.getVariableNames()) {
			final ExternalVariableTypeEnum type = executable.getVariableType(strVariableName);
			if (type.equals(ExternalVariableTypeEnum.Primitive)) {
				message.append(strVariableName + "=" + executable.getVariable(strVariableName) + strNewLine);
			}
		}

		if (message.length() > 0) {

			message.insert(0, strNewLine + "Output variables:" + strNewLine);

			System.out.println(message.toString());
		}
	}

	/**
	 * Create base implementation of ILogger for testing.
	 * 
	 * @param configuredLevel
	 *            log level
	 * @return ILogger
	 */
	private static ILogger createLogger(final LogLevelEnum configuredLevel) {
		return new ILogger() {

			@Override
			public void log(final LogLevelEnum logLevel, final String strMessage) {
				if (isLog(logLevel)) {
					final StringBuilder message = new StringBuilder();
					message.append("log ");
					message.append(logLevel.name());
					message.append(": ");
					message.append(strMessage);

					if (logLevel.equals(LogLevelEnum.ERROR)) {
						System.err.println(message.toString());
					} else {
						System.out.println(message.toString());
					}
				}
			}

			@Override
			public boolean isLog(final LogLevelEnum logLevel) {
				return logLevel.isLessOrEqual(configuredLevel);
			}
		};
	}

	/**
	 * Set external variables into compiler.
	 * 
	 * @param executable
	 *            execution unit
	 * @param variables
	 *            variables from parameters
	 */
	private static void setExtarnalVaribles(final IExecutionUnit executable, final Map<String, String> variables) {

		for (final String strVariableName : executable.getVariableNames()) {
			final String strVariableValue = variables.get(strVariableName);

			final ExternalVariableTypeEnum type = executable.getVariableType(strVariableName);

			if (type.equals(ExternalVariableTypeEnum.Primitive)) {
				executable.setVariable(strVariableName, strVariableValue);

			} else if (type.equals(ExternalVariableTypeEnum.Input)) {
				executable.setInput(strVariableName, createInputStreamProvider(new File(strVariableValue)));

			} else if (type.equals(ExternalVariableTypeEnum.InputArray)) {
				// TODO implement input array
				// final IInputStreamsProvider isp = createIInputStreamsProvider((File[])
				// variable.getValue(null));
				// executable.setInput(variable.getName(), isp);

			} else if (type.equals(ExternalVariableTypeEnum.Output)) {
				executable.setOutput(strVariableName, createOutputStreamProvider(new File(strVariableValue)));

			} else if (type.equals(ExternalVariableTypeEnum.OutputArray)) {
				// TODO implement output array
				// final File[] files = (File[]) variable.getValue(null);
				// if (files.length > 0) {
				// executable.setOutput(variable.getName(),
				// creataIOutputStreamsProvider(files[0]));
				// }
			}

		}

	}

	/**
	 * Create base implementation of InputStreamProvider for testing.
	 * 
	 * @param file
	 *            file
	 * @return InputStreamProvider
	 */
	private static IInputStreamProvider createInputStreamProvider(final File file) {
		return new IInputStreamProvider() {

			@Override
			public void close() throws IOException {
			}

			@Override
			public InputStream openInput() {
				try {
					return new FileInputStream(file);
				} catch (final FileNotFoundException e) {
					e.printStackTrace();
					return null;
				}
			}

			@Override
			public String getProperty(final String strName) {
				return null;
			}

			@Override
			public void setProperty(final String strName, final String strValue) {
			}
		};
	}

	/**
	 * Create base OutputStreamProvider.
	 * 
	 * @param file
	 *            file
	 * @return new OutputStreamProvider
	 */
	private static IOutputStreamProvider createOutputStreamProvider(final File file) {
		return new IOutputStreamProvider() {

			protected HashMap<String, String> properties = new HashMap<>();

			@Override
			public void close() throws IOException {
			}

			@Override
			public InputStream openInput() {
				try {
					return new FileInputStream(file);
				} catch (final FileNotFoundException e) {
					e.printStackTrace();
					return null;
				}
			}

			@Override
			public String getProperty(final String strName) {
				return this.properties.get(strName);
			}

			@Override
			public void setProperty(final String strName, final String strValue) {
				this.properties.put(strName, strValue);
			}

			@Override
			public OutputStream openOutput() {
				try {
					return new FileOutputStream(file);
				} catch (final FileNotFoundException e) {
					e.printStackTrace();
					return null;
				}
			}
		};
	}

}
