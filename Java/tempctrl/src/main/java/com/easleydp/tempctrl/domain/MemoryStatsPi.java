package com.easleydp.tempctrl.domain;

import com.easleydp.tempctrl.util.OsCommandExecuter;

public class MemoryStatsPi extends MemoryStats {
    public MemoryStatsPi() {
        this(calcTotalAndFree());
    }
	private MemoryStatsPi(long[] totalAndFree) {
        super(totalAndFree[0], totalAndFree[1]);
    }

    private static long[] calcTotalAndFree() {
        String[] vmStats = OsCommandExecuter.execute("vmstat", "-s").split("\\n");;
        return new long[] {parseMemory(vmStats, "K total memory"), parseMemory(vmStats, "K free memory")};
	}
	private static long parseMemory(String[] vmStats, String statLabel) {
        for (String vmStat : vmStats) {
            int i = vmStat.indexOf(statLabel);
            if (i != -1) {
                String strKb = vmStat.substring(0, i).trim();
                return 1024L * Integer.parseInt(strKb, 10);
            }
        }
		throw new IllegalStateException("Failed to find vmStat: " + statLabel);
	}
}
