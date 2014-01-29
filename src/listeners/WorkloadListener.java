package listeners;

import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.DynamicElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

import java.util.ArrayList;
import java.util.TreeMap;

import simulator.Metric;
import simulator.MetricKey;

public class WorkloadListener extends ListenerAdapter {

	/**
	 * stores the metrics
	 */
	WorkloadPath _rootPath = new WorkloadPath( null, new TreeMap<MetricKey, Metric>() );
	WorkloadPath _currentPath = _rootPath;
	WorkloadPath HighestCumulativeDescisionWorkloadPath;
	WorkloadPath HighestCumulativeTemporalWorkloadPath;
	WorkloadPath HighestCumulativeResourceWorkloadPath;
	WorkloadPath LowestCumulativeDescisionWorkloadPath;
	WorkloadPath LowestCumulativeTemporalWorkloadPath;
	WorkloadPath LowestCumulativeResourceWorkloadPath;
	
	/**
	 * acts whenever a choice generator is set (at the first point of non-determinism).
	 * at this point we start a new child path.
	 */
	@Override
	public void choiceGeneratorSet ( VM vm, ChoiceGenerator<?> newCG ) {
		makeChoice( newCG.getInsn( ).getMethodInfo( ) );
	}

	/**
	 * acts whenever a choice generator is advanced (at the following points of non-determinism).
	 * at this point we return to the parent path and start a new path.
	 */
	@Override
	public void choiceGeneratorAdvanced ( VM vm, ChoiceGenerator<?> currentCG ) {
		makeChoice( currentCG.getInsn( ).getMethodInfo( ) );
	}
	
	private void makeChoice( MethodInfo mi ) {
		String methodName = mi.getName( );
		if( methodName.equals( "getInt" ) ) {
//			System.out.println("making choice");
			advancePath( );
		}
	}
	
	private void advancePath( ) {
		WorkloadPath newPath = new WorkloadPath( _currentPath, _currentPath.getValues() );
		newPath.setParent( _currentPath );
		_currentPath.addChild( newPath );
		_currentPath = newPath;
	}
	
	/**
	 * acts whenever certain methods execute
	 */
	@Override
	public void executeInstruction ( VM vm, ThreadInfo ti, Instruction insnToExecute ) {
		//get the methods information
		MethodInfo mi = insnToExecute.getMethodInfo( );
		String fullMethodName = mi.getFullName( );

		//only act on these methods
		if ( fullMethodName.contains( "setTransitionDuration" ) )
			storeTransitionDuration( ti, insnToExecute, mi );
		if ( fullMethodName.contains( "setEnabledTransition" ) )
			storeEnabledTransitions( ti, insnToExecute, mi );
		else if ( fullMethodName.contains( "setActiveInput" ) )
			storeActiveInputs( ti, insnToExecute, mi );
		else if ( fullMethodName.contains( "setActiveOutput" ) )
			storeActiveOutputs( ti, insnToExecute, mi );
		else if ( fullMethodName.contains( "endSimulation" ) )
			printCurrentPath();
	}

	private void printCurrentPath() {
		if( HighestCumulativeDescisionWorkloadPath == null || _currentPath.getCumulativeDecisionWorkload( ) > HighestCumulativeDescisionWorkloadPath.getCumulativeDecisionWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "HCDW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeDecisionWorkload() );
			HighestCumulativeDescisionWorkloadPath = _currentPath;
		}
		if( HighestCumulativeResourceWorkloadPath == null || _currentPath.getCumulativeResourceWorkload( ) > HighestCumulativeResourceWorkloadPath.getCumulativeResourceWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "HCRW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeResourceWorkload() );
			HighestCumulativeResourceWorkloadPath = _currentPath;
		}
		if( HighestCumulativeTemporalWorkloadPath == null || _currentPath.getCumulativeTemporalWorkload( ) > HighestCumulativeTemporalWorkloadPath.getCumulativeTemporalWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "HCTW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeTemporalWorkload() );
			HighestCumulativeTemporalWorkloadPath = _currentPath;
		}
		if( LowestCumulativeDescisionWorkloadPath == null || _currentPath.getCumulativeDecisionWorkload( ) < LowestCumulativeDescisionWorkloadPath.getCumulativeDecisionWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "LCDW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeDecisionWorkload() );
			LowestCumulativeDescisionWorkloadPath = _currentPath;
		}
		if( LowestCumulativeResourceWorkloadPath == null || _currentPath.getCumulativeResourceWorkload( ) < LowestCumulativeResourceWorkloadPath.getCumulativeResourceWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "LCRW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeResourceWorkload() );
			LowestCumulativeResourceWorkloadPath = _currentPath;
		}
		if( LowestCumulativeTemporalWorkloadPath == null || _currentPath.getCumulativeTemporalWorkload( ) < LowestCumulativeTemporalWorkloadPath.getCumulativeTemporalWorkload( ) ) {
			CSVPrinter.getInstance( ).print( "LCTW.csv", WorkloadBuilder.build(_currentPath), _currentPath.getCumulativeTemporalWorkload() );
			LowestCumulativeTemporalWorkloadPath = _currentPath;
		}
	}

	private void storeTransitionDuration(ThreadInfo ti,
			Instruction insnToExecute, MethodInfo mi) {
		
		//get parameters
		ArrayList<Object> parameters = getParameters(ti, insnToExecute, mi);
		int time = (int) parameters.get(1);
		String actorName = DEIToString( parameters.get(2) );
		String stateName = DEIToString( parameters.get(3) );
		int duration = (int) parameters.get(4);
		
		//don't measure mock (watered down) model objects
		if( notRecorded( actorName ) )
			return;
		
		//form metrics and keys
		MetricKey currentKey = new MetricKey( time, actorName, stateName, MetricKey.Type.TRANSITION_DURATION );
		Metric currentMetric = new Metric( duration, duration );
		storeMetric(currentKey, currentMetric);
		
	}

	private void storeEnabledTransitions(ThreadInfo ti,
			Instruction insnToExecute, MethodInfo mi) {

		//get parameters
		ArrayList<Object> parameters = getParameters(ti, insnToExecute, mi);
		int time = (int) parameters.get(1);
		String actorName = DEIToString( parameters.get(2) );
		String stateName = DEIToString( parameters.get(3) );
		int transitions = (int) parameters.get(4);

		//don't measure mock (watered down) model objects
		if( notRecorded( actorName ) )
			return;

		//form metrics and keys
		MetricKey currentKey = new MetricKey( time, actorName, stateName, MetricKey.Type.ENABLED_TRANSITION );
		Metric currentMetric = new Metric( transitions, transitions );
		storeMetric(currentKey, currentMetric);
		
	}

	private void storeActiveInputs(ThreadInfo ti, Instruction insnToExecute,
			MethodInfo mi) {
		
		//get parameters
		ArrayList<Object> parameters = getParameters(ti, insnToExecute, mi);
		int time = (int) parameters.get(1);
		String actorName = DEIToString( parameters.get(2) );
		String stateName = DEIToString( parameters.get(3) );
		String input = DEIToString( parameters.get(4) );

		//don't measure mock (watered down) model objects
		if( notRecorded( actorName ) )
			return;

		//form metrics and keys
		MetricKey currentKey = new MetricKey( time, actorName, stateName, MetricKey.Type.ACTIVE_INPUT );
		Metric currentMetric = new Metric( 1, input );
		storeMetric(currentKey, currentMetric);
		
	}

	private void storeActiveOutputs(ThreadInfo ti, Instruction insnToExecute,
			MethodInfo mi) {
		
		//get parameters
		ArrayList<Object> parameters = getParameters(ti, insnToExecute, mi);
		int time = (int) parameters.get(1);
		String actorName = DEIToString( parameters.get(2) );
		String stateName = DEIToString( parameters.get(3) );
		String output = DEIToString( parameters.get(4) );

		//don't measure mock (watered down) model objects
		if( notRecorded( actorName ) )
			return;

		//form metrics and keys
		MetricKey currentKey = new MetricKey( time, actorName, stateName, MetricKey.Type.ACTIVE_OUTPUT );
		Metric currentMetric = new Metric( 1, output );
		storeMetric(currentKey, currentMetric);
		
	}
	
	private ArrayList<Object> getParameters( ThreadInfo ti, Instruction insnToExecute, MethodInfo mi ) {
		ArrayList<Object> parameters = new ArrayList<Object>();
		
		int parameterSize = ti.getStackFrameExecuting( insnToExecute, 0 ).getLocalVariableCount();
		for ( int i = 0; i < parameterSize; i++ ) {
			Object parameter = ti.getStackFrameExecuting( insnToExecute, 0 ).getLocalOrFieldValue( mi.getLocalVar( i, ti.getPC( ).getPosition( ) ).getName() );
			parameters.add( parameter );
		}
		
		return parameters;
	}
	
	private boolean notRecorded( String actorName ) {
		if (actorName.contains("UAV"))
			return true;
		if (actorName.contains("ater"))
			return true;
		return false;
	}
	
	private void storeMetric( MetricKey currentKey, Metric currentMetric) {
		Metric metric = _currentPath.get( currentKey );
		if ( metric == null )
			_currentPath.put( currentKey, currentMetric );
		else
			 metric.add( metric );
	}

	/**
	 * must always return a !null string
	 * @param object a generic object returned by getLocalOrFieldValue
	 * @return a string representation of the object
	 */
	private String DEIToString( Object object ) {
		DynamicElementInfo DEI = ( DynamicElementInfo ) object;
		String result = "";
		
		char[] stringChars = DEI.getStringChars( );
		for( char nextChar : stringChars )
			result += nextChar;
		
		return result;
	}

}
