import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

public class ShamirReconstruct {

    // Fraction helper for exact rational arithmetic
    static class Fr {
        BigInteger num, den;
        Fr(BigInteger n, BigInteger d) {
            if(d.signum() == 0) throw new ArithmeticException("Division by zero in Fr");
            if(d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            num = n.divide(g);
            den = d.divide(g);
        }
        Fr add(Fr o) {
            return new Fr(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
        }
        Fr mul(Fr o) {
            return new Fr(num.multiply(o.num), den.multiply(o.den));
        }
    }

    // Lagrange interpolation to compute f(0) (the secret)
    static BigInteger lagrangeConstant(List<int[]> pts, List<BigInteger> yvals) {
        int k = pts.size();
        Fr res = new Fr(BigInteger.ZERO, BigInteger.ONE);

        for(int j=0;j<k;j++) {
            int xj = pts.get(j)[0];
            BigInteger yj = yvals.get(pts.get(j)[1]);
            Fr num = new Fr(yj, BigInteger.ONE);
            BigInteger den = BigInteger.ONE;

            for(int m=0;m<k;m++) {
                if(m==j) continue;
                int xm = pts.get(m)[0];
                if(xj == xm) throw new IllegalArgumentException("Duplicate share id found: "+xj);
                num = num.mul(new Fr(BigInteger.valueOf(-xm), BigInteger.ONE));
                den = den.multiply(BigInteger.valueOf(xj - xm));
            }
            res = res.add(new Fr(num.num, num.den.multiply(den)));
        }
        return res.num.divide(res.den);
    }

    // Parse JSON shares into points and values
    static void parseInput(String filename, List<int[]> pts, List<BigInteger> yvals, int[] nk) throws Exception {
        String content = Files.readString(Paths.get(filename));
        JSONObject jobj = new JSONObject(content);

        JSONObject keys = jobj.getJSONObject("keys");
        nk[0] = keys.getInt("n");
        nk[1] = keys.getInt("k");

        int idx = 0;
        for(String key : jobj.keySet()) {
            if(key.equals("keys")) continue;
            JSONObject s = jobj.getJSONObject(key);
            int base = Integer.parseInt(s.getString("base"));
            String val = s.getString("value");
            BigInteger dec = new BigInteger(val, base);
            pts.add(new int[]{Integer.parseInt(key), idx});
            yvals.add(dec);
            idx++;
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("Usage: java ShamirReconstruct <input.json> <output.txt>");
            return;
        }

        String inFile = args[0];
        String outFile = args[1];

        List<int[]> pointsAll = new ArrayList<>();
        List<BigInteger> yvals = new ArrayList<>();
        int[] nk = new int[2];

        parseInput(inFile, pointsAll, yvals, nk);
        int n = nk[0], k = nk[1];

        Map<BigInteger,Integer> freq = new HashMap<>();
        Map<BigInteger,List<Set<Integer>>> combMap = new HashMap<>();

        // generate all combinations of size k
        int[] comb = new int[k];
        for(int i=0;i<k;i++) comb[i]=i;

        while(true) {
            List<int[]> comboPts = new ArrayList<>();
            List<BigInteger> yValsForCombo = new ArrayList<>();

            for(int idIdx: comb) {
                int[] p = pointsAll.get(idIdx);
                comboPts.add(new int[]{p[0], yValsForCombo.size()});
                yValsForCombo.add(yvals.get(p[1]));
            }

            try {
                BigInteger secret = lagrangeConstant(comboPts, yValsForCombo);
                freq.put(secret, freq.getOrDefault(secret,0)+1);

                combMap.putIfAbsent(secret,new ArrayList<>());
                Set<Integer> ids = new HashSet<>();
                for(int idIdx: comb) ids.add(pointsAll.get(idIdx)[0]);
                combMap.get(secret).add(ids);
            } catch(Exception e) {
                // skip bad combo
            }

            int i=k-1;
            while(i>=0 && comb[i]==n-k+i) i--;
            if(i<0) break;
            comb[i]++;
            for(int j=i+1;j<k;j++) comb[j]=comb[j-1]+1;
        }

        // Find most consistent secret
        BigInteger secret = null; int best=0;
        for(Map.Entry<BigInteger,Integer> e: freq.entrySet()){
            if(e.getValue()>best){
                best = e.getValue();
                secret = e.getKey();
            }
        }

        // detect corrupted shares
        Set<Integer> validShares = new HashSet<>();
        for(Set<Integer> ids: combMap.get(secret)) validShares.addAll(ids);

        List<Integer> corrupted = new ArrayList<>();
        for(int[] p: pointsAll) if(!validShares.contains(p[0])) corrupted.add(p[0]);

        // Write output
        try(PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("Reconstructed Secret: " + secret);
            pw.println("Corrupted Shares: " + corrupted);
        }

        System.out.println("Done. Output written to "+outFile);
    }
}
