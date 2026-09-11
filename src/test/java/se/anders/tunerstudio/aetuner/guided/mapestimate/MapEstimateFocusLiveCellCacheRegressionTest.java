package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Ensures presentation throttling cannot leave Focus on the previous live table cell. */
public final class MapEstimateFocusLiveCellCacheRegressionTest {
    private MapEstimateFocusLiveCellCacheRegressionTest() { }

    public static void main(String[] args) {
        crossingLiveCellInvalidatesImmediately();
        System.out.println("MapEstimateFocusLiveCellCacheRegressionTest passed");
    }

    private static void crossingLiveCellInvalidatesImmediately() {
        MapEstimateGuidedController controller = new MapEstimateGuidedController(null);
        controller.configure("focus-live-cell-test",
                new double[]{0.0, 20.0, 40.0},
                new double[]{1000.0, 2500.0, 4000.0},
                new double[][]{
                        {45.0, 45.0, 45.0},
                        {60.0, 60.0, 60.0},
                        {80.0, 80.0, 80.0}
                }, 3, 115.0);
        controller.start();

        MapEstimateFocusModel first = controller.focus(2.0, 1100.0, "eligible");
        int firstRow = controller.cachedLiveRowForTest();
        int firstColumn = controller.cachedLiveColumnForTest();
        require(firstRow == 0 && firstColumn == 0,
                "fixture did not start in the first live MAP Estimate cell");

        MapEstimateFocusModel second = controller.focus(39.0, 3900.0, "eligible");
        require(controller.cachedLiveRowForTest() == 2
                        && controller.cachedLiveColumnForTest() == 2,
                "live-cell crossing did not invalidate/update the Focus cache immediately");
        require(second != first,
                "Focus returned the previous cached model after the live cell changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
