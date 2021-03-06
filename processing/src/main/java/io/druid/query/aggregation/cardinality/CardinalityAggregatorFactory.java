/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013, 2014  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.query.aggregation.cardinality;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.druid.query.aggregation.Aggregator;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.Aggregators;
import io.druid.query.aggregation.BufferAggregator;
import io.druid.query.aggregation.hyperloglog.HyperLogLogCollector;
import io.druid.segment.ColumnSelectorFactory;
import io.druid.segment.DimensionSelector;
import org.apache.commons.codec.binary.Base64;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;

public class CardinalityAggregatorFactory implements AggregatorFactory
{
  public static Object estimateCardinality(Object object)
  {
    if (object == null) {
      return 0;
    }

    return ((HyperLogLogCollector) object).estimateCardinality();
  }

  private static final byte CACHE_TYPE_ID = (byte) 0x8;

  private final String name;
  private final List<String> fieldNames;
  private final boolean byRow;

  @JsonCreator
  public CardinalityAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldNames") final List<String> fieldNames,
      @JsonProperty("byRow") final Boolean byRow
  )
  {
    this.name = name;
    this.fieldNames = fieldNames;
    this.byRow = byRow == null ? false : byRow;
  }

  @Override
  public Aggregator factorize(final ColumnSelectorFactory columnFactory)
  {
    List<DimensionSelector> selectors = makeDimensionSelectors(columnFactory);

    if (selectors.isEmpty()) {
      return Aggregators.noopAggregator();
    }

    return new CardinalityAggregator(name, selectors, byRow);
  }


  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory columnFactory)
  {
    List<DimensionSelector> selectors = makeDimensionSelectors(columnFactory);

    if (selectors.isEmpty()) {
      return Aggregators.noopBufferAggregator();
    }

    return new CardinalityBufferAggregator(selectors, byRow);
  }

  private List<DimensionSelector> makeDimensionSelectors(final ColumnSelectorFactory columnFactory)
  {
    return Lists.newArrayList(
        Iterables.filter(
            Iterables.transform(
                fieldNames, new Function<String, DimensionSelector>()
            {
              @Nullable
              @Override
              public DimensionSelector apply(@Nullable String input)
              {
                return columnFactory.makeDimensionSelector(input);
              }
            }
            ), Predicates.notNull()
        )
    );
  }

  @Override
  public Comparator getComparator()
  {
    return new Comparator<HyperLogLogCollector>()
    {
      @Override
      public int compare(HyperLogLogCollector lhs, HyperLogLogCollector rhs)
      {
        return lhs.compareTo(rhs);
      }
    };
  }

  @Override
  public Object combine(Object lhs, Object rhs)
  {
    if (rhs == null) {
      return lhs;
    }
    if (lhs == null) {
      return rhs;
    }
    return ((HyperLogLogCollector) lhs).fold((HyperLogLogCollector) rhs);
  }

  @Override
  public AggregatorFactory getCombiningFactory()
  {
    return new CardinalityAggregatorFactory(name, fieldNames, byRow);
  }

  @Override
  public Object deserialize(Object object)
  {
    if (object instanceof byte[]) {
      return HyperLogLogCollector.makeCollector(ByteBuffer.wrap((byte[]) object));
    } else if (object instanceof ByteBuffer) {
      return HyperLogLogCollector.makeCollector((ByteBuffer) object);
    } else if (object instanceof String) {
      return HyperLogLogCollector.makeCollector(
          ByteBuffer.wrap(Base64.decodeBase64(((String) object).getBytes(Charsets.UTF_8)))
      );
    }
    return object;
  }

  @Override

  public Object finalizeComputation(Object object)
  {
    return estimateCardinality(object);
  }

  @Override
  @JsonProperty
  public String getName()
  {
    return name;
  }

  @Override
  public List<String> requiredFields()
  {
    return fieldNames;
  }

  @JsonProperty
  public List<String> getFieldNames()
  {
    return fieldNames;
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] fieldNameBytes = Joiner.on("\u0001").join(fieldNames).getBytes(Charsets.UTF_8);

    return ByteBuffer.allocate(1 + fieldNameBytes.length)
                     .put(CACHE_TYPE_ID)
                     .put(fieldNameBytes)
                     .array();
  }

  @Override
  public String getTypeName()
  {
    return "hyperUnique";
  }

  @Override
  public int getMaxIntermediateSize()
  {
    return HyperLogLogCollector.getLatestNumBytesForDenseStorage();
  }

  @Override
  public Object getAggregatorStartValue()
  {
    return HyperLogLogCollector.makeLatestCollector();
  }

  @Override
  public String toString()
  {
    return "CardinalityAggregatorFactory{" +
           "name='" + name + '\'' +
           ", fieldNames='" + fieldNames + '\'' +
           '}';
  }
}
