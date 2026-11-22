package digital.jinatra.tidyra.core.model

import org.junit.Assert.*
import org.junit.Test

class OperationProgressTest {
    @Test fun unknownWorkDoesNotInventPercentage() {
        assertNull(OperationProgress("Scanning").fraction)
        assertNull(OperationProgress("Importing", bytesDone = 100).fraction)
    }
    @Test fun countsAndPartialCopiesUseRealWorkAndClampBounds() {
        assertEquals(0f, OperationProgress("Moving", 0, 4).fraction!!, 0f)
        assertEquals(.375f, OperationProgress("Moving", 1, 4, bytesDone = 50, bytesTotal = 100).fraction!!, 0f)
        assertEquals(1f, OperationProgress("Moving", 7, 4).fraction!!, 0f)
        assertEquals(.5f, OperationProgress("Download", bytesDone = 50, bytesTotal = 100).fraction!!, 0f)
    }
}
