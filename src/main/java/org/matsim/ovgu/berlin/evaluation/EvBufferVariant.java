package org.matsim.ovgu.berlin.evaluation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EvBufferVariant {
	public EvBufferVariant(String variantType, String tourDirectory, String versionIdent, double[] expTravelTimes,
			String[] linkIDs) {
		this.variantType = variantType;
		this.versionIdent = versionIdent;
		this.versionDirectory = tourDirectory + "/" + versionIdent + "/";
		this.expTT = expTravelTimes;
		this.linkIDs = linkIDs;
	}

	public String variantType;
	public String versionIdent;
	public String versionDirectory;
	public String[] linkIDs;
	public double[] expTT;

	// scenario / link --> delay
	public double[][] delayScenarios;
	public List<EvBufferSetup> buffers = new ArrayList<EvBufferSetup>();

	public void writeScenariosCSV() {
		try {
			File csvFile = new File(versionDirectory + "/" + versionIdent + "_scenarios.csv");
			csvFile.getParentFile().mkdirs();
			FileWriter csvWriter = new FileWriter(csvFile);

			String str = ";Scenario:";
			if (delayScenarios != null)
				for (int i = 0; i < delayScenarios.length; i++)
					str += ";" + i;
			csvWriter.append(str + "\nexpectedTravelTime\n");

			for (int i = 0; i < expTT.length; i++) {
				str = expTT[i] + ";;";
				if (delayScenarios != null)
					for (int s = 0; s < delayScenarios.length; s++)
						str += delayScenarios[s][i] + ";";
				csvWriter.append(str.replace(".", ",") + "\n");
			}

			csvWriter.flush();
			csvWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void writeOrLoad(boolean write) {
		if (write) {
			for (EvBufferSetup buffer : buffers) {
				buffer.writeParameters();
				buffer.generateRunSettings();
			}
			writeScenariosCSV();
		} else
			loadRunSettings();
	}

	public void loadRunSettings() {
		for (EvBufferSetup buffer : buffers) {
			buffer.readRunSettings();
//			buffer.runSettings.directory = buffer.runSettings.directory.replace("C:\\Users\\koetscha\\Desktop\\develop\\matsim.pave\\pave", "D:\\Rico\\ExperimentsMay2020");
//			buffer.writeRunSettingsCSV();
		}
	}

}
