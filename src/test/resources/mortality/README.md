# Synthetic mortality fixture

`synthetic-qx-fixture-v1.csv` is hand-authored test data. It is not an SSA or
other official actuarial table and must not be used as a production mortality
assumption.

Each row contains attained age and synthetic male/female `qx`, where `qx` is
the probability that a person alive at exact age `x` dies before exact age
`x + 1`. The fixture supports attained ages 70–73 and uses terminal death age
74. It exists only for offline parser, probability, boundary, and integration
tests.
