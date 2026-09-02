package com.b4rrhh.workforceloader.infrastructure.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lista de nombres con peso: unos pocos muy frecuentes y una cola larga, como en un padrón de
 * verdad. Más variedad no es más realismo —310 apellidos distintos se ven más falsos que una
 * plantilla con varios García—; lo que hace falta es que las colisiones bajen de «varias por
 * página» a «alguna en toda la plantilla» (workforce-loader#3).
 */
final class WeightedNameList {

    private final List<String> names;
    private final int[] cumulativeWeights;
    private final int totalWeight;

    private WeightedNameList(List<String> names, int[] cumulativeWeights, int totalWeight) {
        this.names = names;
        this.cumulativeWeights = cumulativeWeights;
        this.totalWeight = totalWeight;
    }

    @SafeVarargs
    static WeightedNameList of(Map.Entry<String, Integer>... weightedNames) {
        List<String> names = new ArrayList<>(weightedNames.length);
        int[] cumulativeWeights = new int[weightedNames.length];
        int total = 0;
        for (int i = 0; i < weightedNames.length; i++) {
            int weight = weightedNames[i].getValue();
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive: " + weightedNames[i]);
            }
            total += weight;
            names.add(weightedNames[i].getKey());
            cumulativeWeights[i] = total;
        }
        return new WeightedNameList(List.copyOf(names), cumulativeWeights, total);
    }

    String pick(Random random) {
        int target = random.nextInt(totalWeight);
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (target < cumulativeWeights[i]) {
                return names.get(i);
            }
        }
        throw new IllegalStateException("unreachable: cumulative weights do not cover " + target);
    }

    List<String> names() {
        return names;
    }
}
