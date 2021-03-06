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
package com.linkedin.photon.ml.stat

import breeze.linalg.{DenseMatrix, max => Bmax, min => Bmin, norm => Bnorm}
import breeze.stats.{MeanAndVariance, meanAndVariance}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.testng.Assert._
import org.testng.annotations.{DataProvider, Test}

import com.linkedin.photon.ml.Types.SparkVector
import com.linkedin.photon.ml.constants.MathConst
import com.linkedin.photon.ml.data.LabeledPoint
import com.linkedin.photon.ml.test.Assertions.assertIterableEqualsWithTolerance
import com.linkedin.photon.ml.test.SparkTestUtils
import com.linkedin.photon.ml.util.VectorUtils

/**
 * Tests for BasicStatisticalSummary.
 */
class BasicStatisticalSummaryTest extends SparkTestUtils {

  private val EPSILON: Double = MathConst.HIGH_PRECISION_TOLERANCE_THRESHOLD

  /**
   * A trivial set of fixed labeled points for simple tests to verify by hand.
   *
   * @note I wanted to depend on GameTestUtils where this is already located, but apparently GameTestUtils is not in the
   *       right place
   *
   * @return A single set of 10 vectors of 2 features and a label.
   */
  @DataProvider
  def trivialLabeledPoints(): Array[Array[Seq[LabeledPoint]]] =

  Array(Array(Seq(
    LabeledPoint(0.0, Vectors.dense(-0.7306653538519616, 0.0)),
    LabeledPoint(1.0, Vectors.dense(0.6750417712898752, -0.4232874171873786)),
    LabeledPoint(1.0, Vectors.dense(0.1863463229359709, -0.8163423997075965)),
    LabeledPoint(0.0, Vectors.dense(-0.6719842051493347, 0.0)),
    LabeledPoint(1.0, Vectors.dense(0.9699938346531928, 0.0)),
    LabeledPoint(1.0, Vectors.dense(0.22759406190283604, 0.0)),
    LabeledPoint(1.0, Vectors.dense(0.9688721028330911, 0.0)),
    LabeledPoint(0.0, Vectors.dense(0.5993795346650845, 0.0)),
    LabeledPoint(0.0, Vectors.dense(0.9219423508390701, -0.8972778242305388)),
    LabeledPoint(0.0, Vectors.dense(0.7006904841584055, -0.5607635619919824)))))

  /**
   * This test is  useful to check what we do in our own wrapper around spark.ml MultivariateStatisticalSummary.
   *
   * @param labeledPoints Some set labeled points for which we know the correct answer
   */
  @Test(dataProvider = "trivialLabeledPoints")
  def testBasicStatisticsWithKnownResults(labeledPoints: Seq[LabeledPoint]): Unit = sparkTest("testBasicStatisticsWithKnownResults") {

    val featureShardId = "features"

    val trainingData: DataFrame = new SQLContext(sc)
      .createDataFrame(labeledPoints
        .map { point: LabeledPoint => (point.label, VectorUtils.breezeToMllib(point.features)) })
      .toDF("response", featureShardId)

    // Calling rdd explicitly here to avoid a typed encoder lookup in Spark 2.1
    val stats = BasicStatisticalSummary(trainingData.select(featureShardId).rdd.map(_.getAs[SparkVector](0)))

    assertEquals(stats.count, 10)
    assertEquals(stats.mean(0), 0.3847210904276229, EPSILON)
    assertEquals(stats.mean(1), -0.26976712031174965, EPSILON)
    assertEquals(stats.variance(0), 0.40303763661250336, EPSILON)
    assertEquals(stats.variance(1), 0.13748971393448942, EPSILON)
    assertEquals(stats.numNonzeros(0), 10.0)
    assertEquals(stats.numNonzeros(1), 4.0)
    assertEquals(stats.max(0), 0.9699938346531928, EPSILON)
    assertEquals(stats.max(1), 0.0, EPSILON)
    assertEquals(stats.min(0), -0.7306653538519616, EPSILON)
    assertEquals(stats.min(1), -0.8972778242305388, EPSILON)
    assertEquals(stats.normL1(0), 6.652510022278823, EPSILON)
    assertEquals(stats.normL1(1), 2.6976712031174963, EPSILON)
    assertEquals(stats.normL2(0), 2.2599650226741836, EPSILON)
    assertEquals(stats.normL2(1), 1.401838227979015, EPSILON)
    assertEquals(stats.meanAbs(0), 0.6652510022278822, EPSILON)
    assertEquals(stats.meanAbs(1), 0.26976712031174965, EPSILON)
  }

  /**
   * A more extensive test, where correct results are calculated rather than fixed.
   */
  @Test
  def testBasicStatistics(): Unit = sparkTest("testBasicStatistics") {

    val NUM_POINTS: Int = 1000
    val NUM_FEATURES: Int = 100
    val SEED: Int = 0

    val labeledPoints =
      drawBalancedSampleFromNumericallyBenignDenseFeaturesForBinaryClassifierLocal(SEED, NUM_POINTS, NUM_FEATURES)
        .map(obj => new LabeledPoint(label = obj._1, obj._2, offset = 0, weight = 1)).toList
    val dataRdd = sc.parallelize(labeledPoints)
    val summary = BasicStatisticalSummary(dataRdd)
    assertEquals(summary.count, NUM_POINTS.toLong)
    val allElements = labeledPoints.map(x => x.features.toArray).reduceLeft((x, y) => x ++: y)
    // A matrix with columns representing points and rows representing features.
    // The matrix is filled in column major order.
    val matrix = new DenseMatrix(NUM_FEATURES, NUM_POINTS, allElements)

    val items = for (i <- 0 until NUM_FEATURES) yield {
      // Get the i-th row and transpose to a vector. Similar to MATLAB syntax
      val vector = matrix(i, ::).t
      val oneNormL1 = Bnorm(vector, 1)
      val oneNormL2 = Bnorm(vector, 2)
      val oneMax = Bmax(vector)
      val oneMin = Bmin(vector)
      val mV: MeanAndVariance = meanAndVariance(vector)
      val oneNumNonzeros = vector.toArray.count(_ != 0).toDouble
      (oneNormL1, oneNormL2, oneMax, oneMin, mV.mean, mV.variance, oneNumNonzeros)
    }

    val normL1 = items.map(_._1)
    val normL2 = items.map(_._2)
    val meanAbs = normL1.map(_ / labeledPoints.size)
    val max = items.map(_._3)
    val min = items.map(_._4)
    val mean = items.map(_._5)
    val variance = items.map(_._6)
    val numNonzeros = items.map(_._7)

    assertIterableEqualsWithTolerance(summary.max.toArray, max, EPSILON)
    assertIterableEqualsWithTolerance(summary.min.toArray, min, EPSILON)
    assertIterableEqualsWithTolerance(summary.mean.toArray, mean, EPSILON)
    assertIterableEqualsWithTolerance(summary.variance.toArray, variance, EPSILON)
    assertIterableEqualsWithTolerance(summary.normL1.toArray, normL1, EPSILON)
    assertIterableEqualsWithTolerance(summary.normL2.toArray, normL2, EPSILON)
    assertIterableEqualsWithTolerance(summary.numNonzeros.toArray, numNonzeros, EPSILON)
    assertIterableEqualsWithTolerance(summary.meanAbs.toArray, meanAbs, EPSILON)
  }
}
