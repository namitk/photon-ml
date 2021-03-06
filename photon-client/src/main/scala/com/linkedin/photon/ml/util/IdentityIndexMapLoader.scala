/*
 * Copyright 2017 LinkedIn Corp. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linkedin.photon.ml.util

import scala.util.Try

import com.linkedin.photon.ml.Constants

/**
 * A wrapper that loads a pseudo index map that always directly passes through indices other than intercept.
 */
class IdentityIndexMapLoader(val featureDimension: Int, val useIntercept: Boolean = true) extends IndexMapLoader {

  private val map = new IdentityIndexMap(featureDimension)

  private class IdentityIndexMap(val d: Int) extends IndexMap {
    /**
     *
     * @param idx The feature index
     * @return The feature name if found, NONE otherwise
     */
    override def getFeatureName(idx: Int): Option[String] = {
      if (useIntercept && idx == d - 1) {
        Some(Constants.INTERCEPT_KEY)
      } else {
        if (idx >= 0 && idx < d) Some(idx.toString) else None
      }
    }

    /**
     *
     * @param name The feature name
     * @return The feature index if found, IndexMap.NULL_KEY otherwise
     */
    override def getIndex(name: String): Int = {
      if (useIntercept && name == Constants.INTERCEPT_KEY) {
        d - 1
      } else {
        val idx = Try(name.toInt).getOrElse(IndexMap.NULL_KEY)
        if (idx >= 0 && idx < d) idx else IndexMap.NULL_KEY
      }
    }

    /**
     *
     * @param kv
     * @tparam B1
     * @return
     */
    override def +[B1 >: Int](kv: (String, B1)): Map[String, B1] =
      throw new RuntimeException("Operation not supported")

    /**
     *
     * @param key
     * @return
     */
    override def -(key: String): Map[String, Int] =
      throw new RuntimeException("Operation not supported")

    /**
     *
     * @param key
     * @return
     */
    override def get(key: String): Option[Int] = {
      val idx = getIndex(key)
      if (idx == IndexMap.NULL_KEY) None else Some(idx)
    }

    /**
     *
     * @return
     */
    override def iterator: Iterator[(String, Int)] = {
      if (useIntercept) {
        (0 until d - 1).map(i => (i.toString, i)).toIterator ++
          Some((Constants.INTERCEPT_KEY, d - 1)).toIterator
      } else {
        (0 until d).map(i => (i.toString, i)).toIterator
      }
    }

    /**
     *
     * @return
     */
    override def size:Int = d
  }

  /**
   *
   * @return The loaded IndexMap for driver
   */
  override def indexMapForDriver(): IndexMap = map

  /**
   *
   * @return The loaded IndexMap for RDDs
   */
  override def indexMapForRDD(): IndexMap = map
}
