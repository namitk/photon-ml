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
package com.linkedin.photon.ml.diagnostics.reporting.reports.model

import com.linkedin.photon.ml.diagnostics.bootstrap.BootstrapToPhysicalReportTransformer
import com.linkedin.photon.ml.diagnostics.featureimportance.FeatureImportanceToPhysicalReportTransformer
import com.linkedin.photon.ml.diagnostics.fitting.FittingToPhysicalReportTransformer
import com.linkedin.photon.ml.diagnostics.hl.NaiveHosmerLemeshowToPhysicalReportTransformer
import com.linkedin.photon.ml.diagnostics.independence.PredictionErrorIndependencePhysicalReportTransformer
import com.linkedin.photon.ml.diagnostics.reporting._
import com.linkedin.photon.ml.supervised.model.GeneralizedLinearModel

/**
 * Convert model diagnostics into a presentable form.
 */
class ModelDiagnosticToPhysicalReportTransformer[GLM <: GeneralizedLinearModel]
  extends LogicalToPhysicalReportTransformer[ModelDiagnosticReport[GLM], SectionPhysicalReport] {

  import ModelDiagnosticToPhysicalReportTransformer._

  def transform(model: ModelDiagnosticReport[GLM]): SectionPhysicalReport = {
    val metricsSection:SectionPhysicalReport = transformMetrics(model)

    val predErrSection = model.predictionErrorIndependence.map(PREDICTION_ERROR_TRANSFORMER.transform)
    val meanImpactFeatureImportanceSection =
      model.meanImpactFeatureImportance.map(FEATURE_IMPORTANCE_TRANSFORMER.transform)
    val varImpactFeatureImportanceSection =
      model.varianceImpactFeatureImportance.map(FEATURE_IMPORTANCE_TRANSFORMER.transform)

    val hlSection = model.hosmerLemeshow.map(HOSMER_LEMESHOW_TRANSFORMER.transform)

    val fitSection = model.fitReport.map(FIT_TRANSFORMER.transform)
    val bootstrapSection = model.bootstrapReport.map(BOOTSTRAP_TRANSFORMER.transform)

    new SectionPhysicalReport(
      metricsSection :: predErrSection.toList ++ meanImpactFeatureImportanceSection.toList ++
        varImpactFeatureImportanceSection.toList ++ fitSection.toList ++ bootstrapSection.toList ++ hlSection.toList,
      s"$SECTION_TITLE: ${model.modelDescription}, lambda=${model.lambda}")
  }

  private def transformMetrics(model:ModelDiagnosticReport[GLM]): SectionPhysicalReport = {
    new SectionPhysicalReport(
      Seq(
        new BulletedListPhysicalReport(
          model.metrics
            .map(x => s"Metric: [${x._1}, value: [${x._2}]")
            .toSeq
            .sorted
            .map(x => new SimpleTextPhysicalReport(x)))),
      "Validation Set Metrics")
  }
}

object ModelDiagnosticToPhysicalReportTransformer {
  val SECTION_TITLE = "Model Analysis"
  val HOSMER_LEMESHOW_TRANSFORMER = new NaiveHosmerLemeshowToPhysicalReportTransformer()
  val FEATURE_IMPORTANCE_TRANSFORMER = new FeatureImportanceToPhysicalReportTransformer()
  val PREDICTION_ERROR_TRANSFORMER = new PredictionErrorIndependencePhysicalReportTransformer()
  val FIT_TRANSFORMER = new FittingToPhysicalReportTransformer()
  val BOOTSTRAP_TRANSFORMER = new BootstrapToPhysicalReportTransformer()
  val FEATURE_IMPORTANCE_TITLE = "Coefficient Importance Analysis"
}
