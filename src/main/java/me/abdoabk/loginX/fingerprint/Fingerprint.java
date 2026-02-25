package me.abdoabk.loginX.fingerprint;

import me.abdoabk.loginX.util.HashUtil;

public class Fingerprint {

    private final String clientBrand;
    private final int protocolVersion;
    private final String javaVersion;
    private final boolean proxyFlag;
    private final String hash;

    public Fingerprint(String clientBrand, int protocolVersion, String javaVersion, boolean proxyFlag) {
        this.clientBrand = clientBrand;
        this.protocolVersion = protocolVersion;
        this.javaVersion = javaVersion;
        this.proxyFlag = proxyFlag;
        this.hash = HashUtil.sha256(clientBrand + "|" + protocolVersion + "|" + javaVersion + "|" + proxyFlag);
    }

    public String getHash() { return hash; }
    public String getClientBrand() { return clientBrand; }
    public int getProtocolVersion() { return protocolVersion; }
    public String getJavaVersion() { return javaVersion; }
    public boolean isProxyFlag() { return proxyFlag; }

    @Override
    public String toString() {
        return "Fingerprint{brand=" + clientBrand + ", protocol=" + protocolVersion + '}';
    }
}