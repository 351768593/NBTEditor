package nbt;

import java.io.File;
import java.io.IOException;

import nbt.read.NBTReader;
import nbt.record.NBTRecord;

/**
 * Starts the command line nbt viewer. One argument, to specify the file or the
 * standard input, is needed.
 * 
 * @author Joschi <josua.krause@googlemail.com>
 */
public final class MainCommandLine {

  private MainCommandLine() {
    // no constructor
  }

  /**
   * Starts the command line nbt viewer.
   * 
   * @param args One argument is needed. It specifies the file or the standard
   *          input.
   */
  public static void main(final String[] args) {
    if(args.length != 1) {
      printHelp();
      return;
    }
    try {
      final NBTReader read;
      if(args[0].equals("-")) {
        read = new NBTReader(System.in, true);
      } else {
        read = new NBTReader(new File(args[0]));
      }
      final NBTRecord r = read.read();
      read.close();
      System.out.println(new IndentString().indent(r.getFullRepresentation()));
    } catch(final IOException io) {
      printHelp();
      System.err.println("could not read file: " + args[0]);
    }
  }

  private static void printHelp() {
    System.err.println("call with filename or - for STD-IN");
    System.err.println("prints the content of an nbt file");
  }

}
