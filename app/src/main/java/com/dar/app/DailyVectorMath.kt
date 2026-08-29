package com.dar.app

import kotlin.math.sqrt

/** Vector parsing/reduction rules confirmed for the Daily analysis engine. */
object DailyVectorMath {

    fun parseVector(raw: String): List<Double> =
        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toDoubleOrNull() }

    fun vectorToString(v: List<Double>): String = v.joinToString(",")

    /**
     * Reduces/expands a raw recorded-data vector of size r to a fixed dimension p:
     *  - r == p: unchanged
     *  - r < p: zero-padded at the bottom (end)
     *  - r > p: split into p groups — the first (r mod p) groups get (n+1) raw
     *    values, the remaining groups get n values, where n = r / p (floor) —
     *    then each group is averaged, producing a p-dimensional vector.
     *  - r == 0 (action didn't occur that day): a p-dimensional zero vector.
     */
    fun groupToDimension(raw: List<Double>, p: Int): List<Double> {
        require(p >= 1) { "dimension must be >= 1" }
        val r = raw.size
        if (r == 0) return List(p) { 0.0 }
        if (r == p) return raw
        if (r < p) return raw + List(p - r) { 0.0 }

        val n = r / p
        val k = r % p
        val result = ArrayList<Double>(p)
        var index = 0
        for (groupIndex in 0 until p) {
            val groupSize = if (groupIndex < k) n + 1 else n
            if (groupSize == 0) {
                // Only possible if n == 0 and this group is past the k groups of size 1;
                // shouldn't happen since r > p implies n >= 1, but guard anyway.
                result.add(0.0)
                continue
            }
            var sum = 0.0
            for (j in 0 until groupSize) {
                sum += raw[index + j]
            }
            result.add(sum / groupSize)
            index += groupSize
        }
        return result
    }

    /** Zero-pads (at the end) the shorter of two same-season vectors up to [target] dimensions. */
    fun padToDimension(v: List<Double>, target: Int): List<Double> {
        if (v.size >= target) return v
        return v + List(target - v.size) { 0.0 }
    }

    /** Elementwise mean across a list of equal-length vectors. */
    fun meanVector(vectors: List<List<Double>>): List<Double> {
        if (vectors.isEmpty()) return emptyList()
        val dim = vectors[0].size
        val result = DoubleArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) result[i] += v.getOrElse(i) { 0.0 }
        }
        return result.map { it / vectors.size }
    }

    /** Elementwise (population) standard deviation across a list of equal-length vectors. */
    fun stdDevVector(vectors: List<List<Double>>, mean: List<Double> = meanVector(vectors)): List<Double> {
        if (vectors.isEmpty()) return emptyList()
        val dim = mean.size
        val result = DoubleArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) {
                val diff = v.getOrElse(i) { 0.0 } - mean[i]
                result[i] += diff * diff
            }
        }
        return result.map { sqrt(it / vectors.size) }
    }

    /** Treats a vector's own components as a data set and averages them to one scalar. */
    fun scalarMean(v: List<Double>): Double = if (v.isEmpty()) 0.0 else v.sum() / v.size

    fun sum(v: List<Double>): Double = v.sum()

    /** (recorded/parameter)*100, or null when the parameter sum is 0 (undefined — skip the action). */
    fun percentageRate(recordedSum: Double, parameterSum: Double): Double? {
        if (parameterSum == 0.0) return null
        return (recordedSum / parameterSum) * 100.0
    }
}
