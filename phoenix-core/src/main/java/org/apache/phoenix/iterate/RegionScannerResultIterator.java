/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.iterate;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.phoenix.schema.tuple.EncodedColumnQualiferCellsList;
import org.apache.phoenix.schema.tuple.MultiKeyValueTuple;
import org.apache.phoenix.schema.tuple.PositionBasedMultiKeyValueTuple;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.util.ScanUtil;
import org.apache.phoenix.util.ServerUtil;


public class RegionScannerResultIterator extends BaseResultIterator {
    private final RegionScanner scanner;
    private final Pair<Integer, Integer> minMaxQualifiers;
    private final boolean useQualifierAsIndex;
    
    public RegionScannerResultIterator(RegionScanner scanner, Pair<Integer, Integer> minMaxQualifiers, boolean isJoin) {
        this.scanner = scanner;
        this.useQualifierAsIndex = ScanUtil.useQualifierAsIndex(minMaxQualifiers, isJoin);
        this.minMaxQualifiers = minMaxQualifiers;
    }
    
    @Override
    public Tuple next() throws SQLException {
        // XXX: No access here to the region instance to enclose this with startRegionOperation / 
        // stopRegionOperation 
        synchronized (scanner) {
            try {
                // TODO: size
                List<Cell> results = useQualifierAsIndex ? new EncodedColumnQualiferCellsList(minMaxQualifiers.getFirst(), minMaxQualifiers.getSecond()) :  new ArrayList<Cell>();
                // Results are potentially returned even when the return value of s.next is false
                // since this is an indication of whether or not there are more values after the
                // ones returned
                boolean hasMore = scanner.nextRaw(results);
                if (!hasMore && results.isEmpty()) {
                    return null;
                }
                // We instantiate a new tuple because in all cases currently we hang on to it
                // (i.e. to compute and hold onto the TopN).
                Tuple tuple = useQualifierAsIndex ? new PositionBasedMultiKeyValueTuple() : new MultiKeyValueTuple();
                tuple.setKeyValues(results);
                return tuple;
            } catch (IOException e) {
                throw ServerUtil.parseServerException(e);
            }
        }
    }

	@Override
	public String toString() {
		return "RegionScannerResultIterator [scanner=" + scanner + "]";
	}
}
