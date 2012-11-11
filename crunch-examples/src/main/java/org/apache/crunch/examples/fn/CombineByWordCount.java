package org.apache.crunch.examples.fn;

import org.apache.crunch.CombineFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.Pair;

@SuppressWarnings("serial")
public class CombineByWordCount extends CombineFn<Long, String>{

	@Override
	public void process(Pair<Long, Iterable<String>> input, Emitter<Pair<Long, String>> emitter) {
		StringBuffer sb = new StringBuffer();
		for(String str : input.second()){
			sb.append(str);
			sb.append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		emitter.emit(Pair.of(input.first(), sb.toString()));
	}
	
}