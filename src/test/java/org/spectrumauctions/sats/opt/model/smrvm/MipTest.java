/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import java.util.Collection;

import org.junit.Test;
import org.spectrumauctions.sats.core.bidlang.generic.GenericValue;
import org.spectrumauctions.sats.core.model.smrvm.*;
import org.spectrumauctions.sats.core.model.mrvm.MRVMRegionsMap.Region;


/**
 * @author Michael Weiss
 *
 */
public class MipTest {

    @Test
    public void testNoException(){
        Collection<SMRVMBidder> bidders = new SimplifiedMultiRegionModel().createNewPopulation();
        SMRVM_MIP mip = new SMRVM_MIP(bidders);
        MipResult result = mip.calculateAllocation();
        for(SMRVMBidder bidder : bidders){
            GenericValue<SMRVMGenericDefinition> genVal = result.getAllocation(bidder);
            for(Region region : bidder.getWorld().getRegionsMap().getRegions()){
                for(SMRVMBand band : bidder.getWorld().getBands()){
                    SMRVMGenericDefinition def = new SMRVMGenericDefinition(band, region);
                    Integer quantity = genVal.getQuantity(def);
                    System.out.println(new StringBuilder("bidder ").append(bidder.getId()).append("\t").append(def.toString()).append("\t").append(quantity));
                }
            }
            
        }
        System.out.println(result.getTotalValue());
        System.out.println();
    }

}
