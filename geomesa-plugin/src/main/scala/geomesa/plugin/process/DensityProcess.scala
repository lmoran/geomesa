package geomesa.plugin.process

import org.geotools.process.vector.HeatmapProcess
import org.geotools.process.factory.{DescribeParameter, DescribeProcess}
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.data.Query
import org.opengis.coverage.grid.GridGeometry
import geomesa.core.data.AccumuloFeatureReader

@DescribeProcess(
  title = "Heatmap",
  description = "Computes a heatmap surface over a set of data points and outputs as a single-band raster."
)
class DensityProcess extends HeatmapProcess {
  override def invertQuery(@DescribeParameter(name = "radiusPixels", description = "Radius to use for the kernel", min = 0, max = 1) argRadiusPixels: Integer,
                           @DescribeParameter(name = "outputBBOX", description = "Georeferenced bounding box of the output") argOutputEnv: ReferencedEnvelope,
                           @DescribeParameter(name = "outputWidth", description = "Width of the output raster")  argOutputWidth: Integer,
                           @DescribeParameter(name = "outputHeight", description = "Height of the output raster") argOutputHeight: Integer,
                           targetQuery: Query,
                           targetGridGeometry: GridGeometry): Query = {
    val q =
      super.invertQuery(argRadiusPixels,
        argOutputEnv,
        argOutputWidth,
        argOutputHeight,
        targetQuery,
        targetGridGeometry)

    q.getHints().put(AccumuloFeatureReader.DENSITY_KEY, java.lang.Boolean.TRUE)
    q.getHints().put(AccumuloFeatureReader.WIDTH_KEY, argOutputWidth)
    q.getHints().put(AccumuloFeatureReader.HEIGHT_KEY, argOutputHeight)
    q
  }
}
