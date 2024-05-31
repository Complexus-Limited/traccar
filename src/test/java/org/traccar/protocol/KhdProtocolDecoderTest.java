package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

public class KhdProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        var decoder = inject(new KhdProtocolDecoder(null));

        verifyAttribute(decoder, binary(
                "2929A300403099934C2004030943310000000000000000000000007B0000007FFF0E0000E70014000000000018050B01303030314330334437312102007B2203140DDA610D"),
                Position.KEY_DRIVER_UNIQUE_ID, "0001C03D71");

        verifyAttribute(decoder, binary(
                "2929a3003e1680ba0a2304180759500000000000000000000000007b00000080001914000000000000000000162001641b0b0000249002bc58030001cc46020000e70d"),
                Position.KEY_BATTERY_LEVEL, 100);

        verifyPosition(decoder, binary(
                "2929800028258b8c10210731035840031534240542120200000337fb000000ffff5a00000a0000000005005d0d"));

        verifyPosition(decoder, binary(
                "292980002825863156210105095059035109370460010100000211ffff000002fc0000001e780b12000034e70d"));

        verifyPosition(decoder, binary(
                "2929a3003420b2ab46201115115601800115110350825100000133fb00df4bfdff0d000000000000000900000c180887d9ffffffffffff960d"));

        verifyNull(decoder, binary(
                "2929b1000605162935b80d"));

        verifyPosition(decoder, binary(
                "29298e006d1f29402d181117083846801193910365274500000000f80000227ffc3f00001e00500000000000060088000000220019ffc100000000000000000000000000000000007080002000000016ff893839323534303231303734313134323334333639000800233030302e30306e0d"));

        verifyPosition(decoder, binary(
                "2929a3002e1780c663170216203353003060811013839500000114f8000000ffff5000000a00000000000000060102003db70d"));

        verifyPosition(decoder, binary(
                "292980002805162935140108074727801129670365336900000103ffff000082fc0000001e78091b000000360d"));

        verifyPosition(decoder, binary(
                "29298100280A9F9538081228160131022394301140372500000330FF0000007FFC0F00001E000000000034290D"));

        verifyPosition(decoder, binary(
                "29298000280A81850A120310095750005281370061190800000232F848FFBBFFFF0000001E000000000000ED0D"));

        verifyPosition(decoder, binary(
                "29298E00280F80815A121218203116022318461140227000720262FB00077C7FBF5600001E3C3200000000850D"));

        verifyPosition(decoder, binary(
                "29298200230AA2CC391205030505220285947903109550008002078400000002000000000000750D"));

        verifyPosition(decoder, binary(
                "29298500081DD08C22120312174026026545710312541700000000F819C839FFFF1D00001E00500000003AF90D"));

        verifyPosition(decoder, binary(
                "292980002822836665140825142037045343770193879200000050ffff000082fc000004b0780b170000002a0d"));

        verifyPosition(decoder, binary(
                "292980002802425349120811032137022373011140211100000334FFFF000082FC0000001E780913000034DF0D"));

    }

}
