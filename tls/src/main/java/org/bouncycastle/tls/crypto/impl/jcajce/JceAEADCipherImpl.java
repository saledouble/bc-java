package org.bouncycastle.tls.crypto.impl.jcajce;

import java.security.AccessController;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.spec.AEADParameterSpec;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.tls.crypto.impl.TlsAEADCipherImpl;

/**
 * A basic wrapper for a JCE Cipher class to provide the needed AEAD cipher functionality for TLS.
 */
public class JceAEADCipherImpl
    implements TlsAEADCipherImpl
{
    private static final int BUF_SIZE = 32 * 1024;

    private static boolean checkForAEAD()
    {
        return (Boolean)AccessController.doPrivileged(new PrivilegedAction()
        {
            public Object run()
            {
                try
                {
                    return Cipher.class.getMethod("updateAAD", byte[].class) != null;
                }
                catch (Exception ignore)
                {
                    // TODO[logging] Log the fact that we are falling back to BC-specific class
                    return Boolean.FALSE;
                }
            }
        });
    }

    private static final boolean canDoAEAD = checkForAEAD();

    private static String getAlgParamsName(JcaJceHelper helper, String cipherName)
    {
        try
        {
            String algName = cipherName.contains("CCM") ? "CCM" : "GCM";
            helper.createAlgorithmParameters(algName);
            return algName;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private final JcaJceHelper helper;
    private final int cipherMode;
    private final Cipher cipher;
    private final String algorithm;
    private final int keySize;
    private final String algorithmParamsName;

    private SecretKey key;

    public JceAEADCipherImpl(JcaJceHelper helper, String cipherName, String algorithm, int keySize, boolean isEncrypting)
        throws GeneralSecurityException
    {
        this.helper = helper;
        this.cipher = helper.createCipher(cipherName);
        this.algorithm = algorithm;
        this.keySize = keySize;
        this.cipherMode = (isEncrypting) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
        this.algorithmParamsName = getAlgParamsName(helper, cipherName);
    }

    public void setKey(byte[] key, int keyOff, int keyLen)
    {
        if (keySize != keyLen)
        {
            throw new IllegalStateException();
        }

        this.key = new SecretKeySpec(key, keyOff, keyLen, algorithm);
    }

    public void init(byte[] nonce, int macSize, byte[] additionalData)
    {
        try
        {
            if (canDoAEAD && algorithmParamsName != null)
            {
                AlgorithmParameters algParams = helper.createAlgorithmParameters(algorithmParamsName);

                // fortunately CCM and GCM parameters have the same ASN.1 structure
                algParams.init(new GCMParameters(nonce, macSize).getEncoded());

                cipher.init(cipherMode, key, algParams);

                if (additionalData != null && additionalData.length > 0)
                {
                    cipher.update(additionalData);
                }
            }
            else
            {
                // Otherwise fall back to the BC-specific AEADParameterSpec
                cipher.init(cipherMode, key, new AEADParameterSpec(nonce, macSize * 8, additionalData));
            }
        }
        catch (Exception e)
        {
            throw Exceptions.illegalStateException(e.getMessage(), e);
        }
    }

    public int getOutputSize(int inputLength)
    {
        return cipher.getOutputSize(inputLength);
    }

    public int doFinal(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset)
    {
        try
        {
            // to avoid performance issue in FIPS jar  1.0.0-1.0.2
            int totLen = 0;
            while (inputLength > BUF_SIZE)
            {
                totLen += cipher.update(input, inputOffset, BUF_SIZE, output, outputOffset + totLen);

                inputOffset += BUF_SIZE;
                inputLength -= BUF_SIZE;
            }

            totLen += cipher.update(input, inputOffset, inputLength, output, outputOffset + totLen);
            totLen += cipher.doFinal(output, outputOffset + totLen);

            return totLen;
        }
        catch (GeneralSecurityException e)
        {
            throw Exceptions.illegalStateException("", e);
        }
    }
}
