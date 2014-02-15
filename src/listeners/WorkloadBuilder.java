package listeners;

import java.util.Map.Entry;
import java.util.TreeMap;

import simulator.Metric;
import simulator.MetricKey;

public class WorkloadBuilder {

	public static String build( WorkloadPath path ) {
		
		String result = "time, resource data (Actor State [ActiveInput])*, resource workload, temporal data (Actor State [NumberOfEnabledTransitions])*, temporal workload, decision data (Actor State [TransitionDuration])*, decision workload, tasks";
		
		int currentTime = 0;
		int currentResourceWorkload = 0;
		String currentResourceData = "";
		int currentTemporalWorkload = 0;
		String currentTemporalData = "";
		int currentDecisionWorkload = 0;
		String currentDecisionData = "";
		String tasks = "";
		TreeMap<MetricKey, Metric> values = path.getValues();
		for( Entry<MetricKey, Metric> value : values.entrySet() ) {
			MetricKey metricKey = value.getKey();
			Metric metric = value.getValue();
			
			if ( currentTime != metricKey.getTime() ) {
				result += "\n" + currentTime + "," + currentResourceData + "," + currentResourceWorkload + "," + currentTemporalData + "," + currentTemporalWorkload + "," + currentDecisionData + "," + currentDecisionWorkload + "," + tasks;

				currentResourceWorkload = 0;
				currentResourceData = "";
				currentTemporalWorkload = 0;
				currentTemporalData = "";
				currentDecisionWorkload = 0;
				currentDecisionData = "";
				tasks = "";
			}
			
//			System.out.println(metricKey.toString() + "\n\t" + metric.toString());
			
			currentTime = metricKey.getTime();
			
			if ( metricKey.getType() == MetricKey.Type.ACTIVE_INPUT ) {
				currentResourceWorkload += metric.getValue();
				currentResourceData += "(" + metricKey.getActor() + " " + metricKey.getState() + " " + metric.getData() + ")";
			} else if ( metricKey.getType() == MetricKey.Type.TRANSITION_DURATION ) {
				currentTemporalWorkload += metric.getValue();
				currentTemporalData += "(" + metricKey.getActor() + " " + metricKey.getState() + " " + metric.getData() + ")";
			} else if ( metricKey.getType() == MetricKey.Type.ENABLED_TRANSITION ) {
				currentDecisionWorkload += metric.getValue();
				currentDecisionData += "(" + metricKey.getActor() + " " + metricKey.getState() + " " + metric.getData() + ")";
			} else if ( metricKey.getType() == MetricKey.Type.ACTIVE_OUTPUT ) {
				String taskData = metric.getData().toString();
				String lastData = "";
				
				boolean hasMultiple = false;
				while ( taskData.contains("__") ) {
					hasMultiple = true;
					int divisionIndex = taskData.indexOf( "__" );
					lastData = taskData.substring( 0, divisionIndex + 2 ).replace( "__", "]" );
					taskData = "[" + taskData.substring( divisionIndex + 2 );
					if( lastData.contains("_START_") || lastData.contains("_STOP_") ) {
						tasks += "(" + lastData + ")";
					}
				}
				
				if( !hasMultiple ) {
					lastData = taskData;
					if( lastData.contains("_START_") || lastData.contains("_STOP_") ) {
						tasks += "(" + lastData + ")";
					}
				}
			} 
		}
		result += "\n" + currentTime + "," + currentResourceData + "," + currentResourceWorkload + "," + currentTemporalData + "," + currentTemporalWorkload + "," + currentDecisionData + "," + currentDecisionWorkload + "," + tasks;
		
		return result;
	}
	
}
