package listeners;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CSVPrinter {
	static CSVPrinter _printer;
	
	public static CSVPrinter getInstance( ) {
		// TODO Auto-generated method stub
		if( _printer == null )
			_printer = new CSVPrinter( );
		return _printer;
	}
	
	protected void print( String filename, String string, int workload ) {
		System.out.println("printed " + filename + " with " + workload + " workload");
		try {
			FileWriter writer = new FileWriter( new File( "src/csvFiles/" + filename ) );
	
			writer.write( string );
			
			writer.close();
		} catch ( IOException e ) {
			System.err.println( "-- couldn't print " + filename + ".csv --" );
			e.printStackTrace( );
		}
	}
	
}
