/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.impl.mr.plan;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.crunch.impl.mr.collect.PCollectionImpl;
import org.apache.crunch.impl.mr.collect.PGroupedTableImpl;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 *
 */
public class Edge {
  private final Vertex head;
  private final Vertex tail;
  private final Set<NodePath> paths;
  
  public Edge(Vertex head, Vertex tail) {
    this.head = head;
    this.tail = tail;
    this.paths = Sets.newHashSet();
  }
  
  public Vertex getHead() {
    return head;
  }
  
  public Vertex getTail() {
    return tail;
  }

  public void addNodePath(NodePath path) {
    this.paths.add(path);
  }
  
  public void addAllNodePaths(Collection<NodePath> paths) {
    this.paths.addAll(paths);
  }
  
  public Set<NodePath> getNodePaths() {
    return paths;
  }
  
  public PCollectionImpl getSplit() {
	MSCROptimizer optimizer = new MSCROptimizer(this.paths);
    return Iterables.getFirst(paths, null).get(optimizer.getSplitIndex());
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Edge)) {
      return false;
    }
    Edge e = (Edge) other;
    return head.equals(e.head) && tail.equals(e.tail);
  }
  
  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(head).append(tail).toHashCode();
  }
  
  @Override
  public String toString(){
	  return "E(" + head.toString() + "->" + tail.toString() + ")";
  }
}
