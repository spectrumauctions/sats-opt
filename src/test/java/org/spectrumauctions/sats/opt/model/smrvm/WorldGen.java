/**
 * Copyright by Michael Weiss, weiss.michael@gmx.ch
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.spectrumauctions.sats.opt.model.smrvm;

import org.spectrumauctions.sats.core.model.smrvm.SMRVMGlobalBidderSetup;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMLocalBidderSetup;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMRegionalBidderSetup;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMWorldSetup;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMWorldSetup.BandSetup;
import org.spectrumauctions.sats.core.model.smrvm.SMRVMWorldSetup.SMRVMWorldSetupBuilder;
import org.spectrumauctions.sats.core.util.math.LinearFunction;
import org.spectrumauctions.sats.core.util.random.DoubleInterval;
import org.spectrumauctions.sats.core.util.random.IntegerInterval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael Weiss
 *
 */
public abstract class WorldGen {

    public static String BAND_A_NAME ="TEST_A";
    public static String BAND_B_NAME ="TEST_B";


    static SMRVMWorldSetup getSimpleWorldBuilder(){
        return getSimpleWorldBuilder(5);   
    }
    
    static SMRVMWorldSetup getSimpleWorldBuilder(int numberOfRegions){
        SMRVMWorldSetupBuilder builder = new SMRVMWorldSetupBuilder();
        builder.createGraphRandomly(new IntegerInterval(numberOfRegions), new IntegerInterval(2), 100, 0);
        List<BandSetup> toDelete = new ArrayList<>(builder.bandSetups().values());
        for(BandSetup bandSetup : toDelete){
            builder.removeBandSetup(bandSetup.getName());
        }       
        //Band with 2 lots and base capacity 20 where synergy = quantity
        builder.putBandSetup(new BandSetup(
                BAND_A_NAME, 
                new IntegerInterval(2), 
                new DoubleInterval(20),
                new LinearFunction(BigDecimal.ONE, BigDecimal.ZERO))); //Syngery = Quantity
        //Band with 6 lots and base capacity 10, where synergy = quantity
        builder.putBandSetup(new BandSetup(
                BAND_B_NAME, 
                new IntegerInterval(6), 
                new DoubleInterval(10), 
                new LinearFunction(BigDecimal.ZERO, BigDecimal.ONE))); //No synergy, i.e., synergy constant 1
        return builder.build();      
    }
    
    
    public static SMRVMLocalBidderSetup getSimpleLocalBidderSetup(){
        SMRVMLocalBidderSetup.Builder builder = new SMRVMLocalBidderSetup.Builder();
        builder.setSetupName("Simple Local Bidder");
        builder.setNumberOfBidders(2);
        builder.setAlphaInterval(new DoubleInterval(0.4));
        builder.setBetaInterval(new DoubleInterval(0.5));
        builder.setNumberOfRegionsInterval(new IntegerInterval(3));
        return builder.build();
    }
    
    static SMRVMRegionalBidderSetup getSimpleRegionalBidderSetup(){
        SMRVMRegionalBidderSetup.Builder builder = new SMRVMRegionalBidderSetup.Builder();
        builder.setSetupName("Simple Regional Bidder");
        builder.setNumberOfBidders(1);
        builder.setAlphaInterval(new DoubleInterval(0.4));
        builder.setBetaInterval(new DoubleInterval(0.5));
        builder.setGammaShape(2, 2);
        return builder.build();
    }
    
    static SMRVMGlobalBidderSetup getSimpleGlobalBidderSetup(){
        SMRVMGlobalBidderSetup.Builder builder = new SMRVMGlobalBidderSetup.Builder();
        builder.setSetupName("Simple Global Bidder");
        builder.setNumberOfBidders(1);
        builder.setAlphaInterval(new DoubleInterval(0.4));
        builder.setBetaInterval(new DoubleInterval(0.5));
        return builder.build();
    }
    
  
       

}
