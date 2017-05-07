/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.spectrumauctions.sats.core.bidlang.generic.GenericValue;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMBidder;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMGenericDefinition;
import org.spectrumauctions.sats.core.model.smrvm.SimplifiedMultiRegionModel;

/**
 * @author Michael Weiss
 *
 */
public class OverallValueTest {

    public static String LOW_PAIRED_NAME = "LOW_PAIRED";
    public static String HIGH_PAIRED_NAME = "HIGH_PAIRED";
    public static String UNPAIRED_NAME = "UNPAIRED";
    
    @Test
    public void mipValuesEqualSATSValues(){
        List<SMRVMBidder> bidders = new SimplifiedMultiRegionModel().createNewPopulation();
        SMRVM_MIP mip = new SMRVM_MIP(bidders);
        MipResult result = mip.calculateAllocation();
        for(SMRVMBidder bidder : bidders){
            GenericValue<SMRVMGenericDefinition> outcomeVal = result.getAllocation(bidder);
            BigDecimal satsVal = bidder.calculateValue(outcomeVal.getQuantities());
            Assert.assertEquals(satsVal.doubleValue(), outcomeVal.getValue().doubleValue(), 0.1);
        }
    }
}
