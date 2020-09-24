package it.bz.idm.bdp.ninja.utils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {

	private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

	private FileUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static String loadFile(final String filename) {
		LOG.debug("Loading file: {}.", filename);
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream in = classloader.getResourceAsStream(filename);
		try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
			return scanner.useDelimiter("\\A").next();
		}
	}

	public static String replacements(final String input, String... replacements) {
		String result = input;
		for (int i = 0; i < replacements.length; i += 2) {
			result = result.replace(replacements[i], replacements[i + 1]);
		}
		return result;
	}
}
