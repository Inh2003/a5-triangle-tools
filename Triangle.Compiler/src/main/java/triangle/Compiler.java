/*
 * @(#)Compiler.java                       
 * 
 * Revisions and updates (c) 2022-2023 Sandy Brownlee. alexander.brownlee@stir.ac.uk
 * 
 * Original release:
 *
 * Copyright (C) 1999, 2003 D.A. Watt and D.F. Brown
 * Dept. of Computing Science, University of Glasgow, Glasgow G12 8QQ Scotland
 * and School of Computer and Math Sciences, The Robert Gordon University,
 * St. Andrew Street, Aberdeen AB25 1HG, Scotland.
 * All rights reserved.
 *
 * This software is provided free for educational use only. It may
 * not be used for commercial purposes without the prior written permission
 * of the authors.
 */

package triangle;

import triangle.abstractSyntaxTrees.Program;
import triangle.codeGenerator.Emitter;
import triangle.codeGenerator.Encoder;
import triangle.contextualAnalyzer.Checker;
import triangle.optimiser.ConstantFolder;
import triangle.syntacticAnalyzer.Parser;
import triangle.syntacticAnalyzer.Scanner;
import triangle.syntacticAnalyzer.SourceFile;
import triangle.treeDrawer.Drawer;
import org.apache.commons.cli.*;


/**
 * The main driver class for the Triangle compiler.
 *
 * @version 2.1 7 Oct 2003
 * @author Deryck F. Brown
 */
public class Compiler {

	/** The filename for the object program, normally obj.tam. */
	static String objectName = "obj.tam";
	
	static boolean showTree = false;
	static boolean folding = false;
	static boolean showTreeAfterFolding = false;
	
	private static boolean generateStats = false;
	private static Scanner scanner;
	private static Parser parser;
	private static Checker checker;
	private static Encoder encoder;
	private static Emitter emitter;
	private static ErrorReporter reporter;
	private static Drawer drawer;

	/** The AST representing the source program. */
	private static Program theAST;

	/**
	 * Compile the source program to TAM machine code.
	 *
	 * @param sourceName   the name of the file containing the source program.
	 * @param objectName   the name of the file containing the object program.
	 * @param showingAST   true iff the AST is to be displayed after contextual
	 *                     analysis
	 * @param showingTable true iff the object description details are to be
	 *                     displayed during code generation (not currently
	 *                     implemented).
	 * @return true iff the source program is free of compile-time errors, otherwise
	 *         false.
	 */
	static boolean compileProgram(String sourceName, String objectName, boolean showingAST, boolean showingTable) {

		System.out.println("********** " + "Triangle Compiler (Java Version 2.1)" + " **********");

		System.out.println("Syntactic Analysis ...");
		SourceFile source = SourceFile.ofPath(sourceName);

		if (source == null) {
			System.out.println("Can't access source file " + sourceName);
			System.exit(1);
		}

		scanner = new Scanner(source);
		reporter = new ErrorReporter(false);
		parser = new Parser(scanner, reporter);
		checker = new Checker(reporter);
		emitter = new Emitter(reporter);
		encoder = new Encoder(emitter, reporter);
		drawer = new Drawer();

		// scanner.enableDebugging();
		theAST = parser.parseProgram(); // 1st pass
		if (reporter.getNumErrors() == 0) {
			// if (showingAST) {
			// drawer.draw(theAST);
			// }
			System.out.println("Contextual Analysis ...");
			checker.check(theAST); // 2nd pass
			if (showingAST) {
				drawer.draw(theAST);
			}
			if (folding) {
				theAST.visit(new ConstantFolder());
			}
			
			StatisticsGenerator statisticsGenerator = new StatisticsGenerator();
		    statisticsGenerator.generateStatistics(theAST);
			
			if (reporter.getNumErrors() == 0) {
				System.out.println("Code Generation ...");
				encoder.encodeRun(theAST, showingTable); // 3rd pass
			}
		}

		boolean successful = (reporter.getNumErrors() == 0);
		if (successful) {
			emitter.saveObjectProgram(objectName);
			System.out.println("Compilation was successful.");
		} else {
			System.out.println("Compilation was unsuccessful.");
		}
		return successful;
	}

	/**
	 * Triangle compiler main program.
	 *
	 * @param args the only command-line argument to the program specifies the
	 *             source filename.
	 */
	public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: tc filename [-o=outputfilename] [tree] [folding] [tree-after-folding] [stats]");
            System.exit(1);
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(getOptions(), args);
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            System.exit(1);
        }

        showTree = cmd.hasOption("tree");
        folding = cmd.hasOption("folding");
        showTreeAfterFolding = cmd.hasOption("tree-after-folding");
        generateStats = cmd.hasOption("stats"); // Check for the "stats" option

        if (cmd.hasOption("o")) {
            objectName = cmd.getOptionValue("o");
        }

        String sourceName = args[0];
        var compiledOK = compileProgram(sourceName, objectName, showTree, showTreeAfterFolding);

        if (generateStats) {
            generateStatistics();
        }

        if (!showTree && !showTreeAfterFolding) {
            System.exit(compiledOK ? 0 : 1);
        }
    }
	
	private static void generateStatistics() {
        if (theAST != null) {
            System.out.println("Generating and printing statistics...");
            StatisticsGenerator statisticsGenerator = new StatisticsGenerator();
            statisticsGenerator.generateStatistics(theAST);
        } else {
            System.out.println("No AST available for statistics generation.");
        }
    }
	
	
	/**
	 * @param getOptions Command line arguments parser
	 *
	 */
	private static Options getOptions() {
        Options options = new Options();

        options.addOption("o", "output", true, "Output filename");
        options.addOption("tree", false, "Display AST");
        options.addOption("folding", false, "Enable constant folding");
        options.addOption("taf", "tree-after-folding", false, "Display AST after folding");
        options.addOption("stats", false, "Generate and print statistics");

        return options;
    }
}
