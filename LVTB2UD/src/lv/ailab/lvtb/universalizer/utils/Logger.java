package lv.ailab.lvtb.universalizer.utils;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Class for printing out in the log files various kinds of additional
 * information. Currently it does warning logging.
 * It is planned to add ID mapping logging.
 */
public class Logger
{
	protected PrintWriter statusOut;
	protected PrintWriter idMappingOut = null;

	/**
	 * To avoid repetitive messages, any message once printed are remembered in
	 * the scope of one sentence.
 	 */
	protected HashSet<String> warnings;
	protected ArrayList<String> idMappingDesc;

	public Logger(String statusOutPath, String logOutPath)
			throws FileNotFoundException, UnsupportedEncodingException
	{
		statusOut = new PrintWriter(new PrintWriter(statusOutPath, "UTF-8"), true);
		if (logOutPath != null && !logOutPath.isEmpty())
			idMappingOut = new PrintWriter(new PrintWriter(logOutPath, "UTF-8"), true);
		warnings = new HashSet<>();
		idMappingDesc = new ArrayList<>();
	}

	public Logger(PrintWriter statusOut, PrintWriter idMappingOut)
	{
		this.statusOut = statusOut;
		this.idMappingOut = idMappingOut;
		warnings = new HashSet<>();
		idMappingDesc = new ArrayList<>();
	}
	public void startFile(String fileName)
	{
		statusOut.printf("Processing file \"%s\", ", fileName);
	}
	public void printFoundTreesCount(int treeCount)
	{
		statusOut.printf("%s trees found...\n", treeCount);
	}

	public void finishFileNormal(boolean empty)
	{
		// Last sentence should have ended in a emptied idMappingDesc.
		if (!idMappingDesc.isEmpty())
			throw new IllegalArgumentException(
					"Even if file ends in a normal way, Logger.idMappingDesc is not empty!");
		if (empty)
			statusOut.printf("Finished - nothing to write.\n");
		else
			statusOut.printf("Finished.\n");
	}
	public void finishFileWithException(Exception e)
	{
		// It is possible, that last sentence has not ended well.
		finishSentenceNormal(true);
		statusOut.printf("File failed with exception: ");
		e.printStackTrace(statusOut);
	}
	public void finishFileWithBadExt(String fileName)
	{
		statusOut.printf(
				"Oops! Unexpected extension for file \"%s\"!\n", fileName);
	}
	public void finishFileWithAUTO()
	{
		statusOut.printf(
				"File starts with \"AUTO\" comment, everything is ommited!\n");
	}

	public void finishSentenceNormal(boolean hasFailed)
	{
		if (!hasFailed && idMappingOut != null)
		{
			for (String s : idMappingDesc)
				idMappingOut.println(s);
			if (!idMappingDesc.isEmpty()) idMappingOut.println();
		}
		warnings = new HashSet<>();
		idMappingDesc = new ArrayList<>();
		flush();
	}
	public void finishSentenceWithException(String treeId, Exception e, boolean algorithmic)
	{
		if (algorithmic)
			statusOut.printf("Transforming sentence %s completely failed! Might be algorithmic error.\n", treeId);
		else
			statusOut.printf("Transforming sentence %s completely failed! Check structure and try again.\n", treeId);
		//statusOut.printf("A sentence %s failed with an exception: ", treeId);
		e.printStackTrace(statusOut);
		finishSentenceNormal(true);
	}
	public void finishSentenceWithFIXME()
	{
		statusOut.printf("A sentence with \"FIXME\" ommited.\n");
		finishSentenceNormal(true);
	}
	public void finishSentenceWithOmit(String treeId)
	{
		statusOut.printf("Sentence %s is being omitted.\n", treeId);
		finishSentenceNormal(true);
	}

	public void warnForAnalyzerException(Exception e)
	{
		statusOut.printf("Analyzer failed, probably while reading lexicon: %s\n", e.getMessage());
	}
	public void doInsentenceWarning(String warning)
	{
		if (!warnings.contains(warning))
		{
			statusOut.println(warning);
			warnings.add(warning);
		}
	}
	public void addIdMapping(String sentenceID, String tokFirstCol, String lvtbNodeId)
	{
		idMappingDesc.add(String.format(
				"%s#%s\t%s", sentenceID, tokFirstCol, lvtbNodeId));
	}

	public void finalStatsAndClose(int omittedFiles, int omittedTrees)
	{
		if (omittedFiles == 0 && omittedTrees == 0)
			statusOut.printf("Everything is finished, nothing was omited.\n");
		else if (omittedFiles == 0)
			statusOut.printf(
					"Everything is finished, %s trees was omited.\n", omittedTrees);
		else
			statusOut.printf(
					"Everything is finished, %s files and at least %s trees was omited.\n",
					omittedFiles, omittedTrees);

		flush();
		statusOut.close();
		if (idMappingOut != null) idMappingOut.close();
	}

	public void flush()
	{
		statusOut.flush();
		if (idMappingOut != null) idMappingOut.flush();
	}
}
