package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class RunCityLC {
    public static void main(String[] args){
        Config config = ConfigUtils.loadConfig(
                "/Users/zekiaga/Documents/MATSIM/matsim_forT/original-input-data/ConfigLaceja.xml");

        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controller().setLastIteration(5);

        config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);
        // modeVehicleTypesFromVehiclesData: at the start of every iteration
        // MATSim creates one vehicle of type "car" for every person whose
        // current plan has a car leg — including agents newly switched to car
        // by SubtourModeChoice.  This eliminates the deprecated
        // usePersonIdForMissingVehicleId fallback.
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        {
            Collection<String>modes = new ArrayList<>();
            modes.add(TransportMode.car);
            config.routing().setNetworkModes(modes);
        }

        // ── Replanning ────────────────────────────────────────────────────
        {
            ReplanningConfigGroup.StrategySettings smc = new ReplanningConfigGroup.StrategySettings();
            smc.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice);
            smc.setWeight(0.1);
            config.replanning().addStrategySettings(smc);

            // Modes found in Madrid_Plans.xml: car=76, pt=56, walk=41, bike=38.
            // car is chain-based: agents must return it to where they parked it.
            String[] allModes        = {"car", "pt", "walk", "bike"};
            String[] chainBasedModes = {"car"};
            config.subtourModeChoice().setModes(allModes);
            config.subtourModeChoice().setChainBasedModes(chainBasedModes);
        }

        // ── Scoring — mode parameters ─────────────────────────────────────
        // Required for every mode that SubtourModeChoice can assign.
        {
            for (String mode : new String[]{"car", "walk", "bike", "pt"}) {
                config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(mode));
            }
        }

        // ── Load scenario (reads Madrid_Network.xml + Madrid_Plans.xml) ───
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // ── Vehicle type ──────────────────────────────────────────────────
        // Required by modeVehicleTypesFromVehiclesData: the VehicleType ID
        // must match the mode name ("car").  MATSim uses this type to create
        // one vehicle per car-using person at the start of each iteration.
        {
            VehicleType carType = VehicleUtils.createVehicleType(Id.create("car", VehicleType.class));
            carType.setMaximumVelocity(50.0 / 3.6);   // 50 km/h — Madrid urban speed
            scenario.getVehicles().addVehicleType(carType);
        }

        // ── Clean the car sub-network ─────────────────────────────────────
        // Madrid_Network.xml has mixed modes (car=1114, walk=2278, bike=33).
        // cleanNetwork keeps only the largest strongly-connected component
        // for "car", removing walk/bike-only islands that would otherwise
        // cause "Network for mode 'car' has unreachable links" crash.
        NetworkUtils.cleanNetwork(scenario.getNetwork(), Set.of(TransportMode.car));

        Controler controler = new Controler(scenario);
        controler.run();
    }
}
