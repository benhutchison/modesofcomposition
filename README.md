# Modes of composition

Workshop in Compositional Functional Programming in Scala, developed by Ben Hutchison (`brhutchison@gmail.com`)

## Workshop History

- Scheduled Lambdajam Online 2021, May 3 & 4, 10am
- Presented Lambdajam Online 2020, July 20 & 21 2020, 10am 

## Layout

The workshop consists of a [conceptual overview slidepack](ModesCompositionSlides.pdf) followed by a progression of structured, guided exercises
that illustrate how the concepts show up in practical code.

Start with the [introduction slidepack](ModesCompositionSlides.pdf). Then each exercise should be attempted in order. The goal is to get the code to compile against the provided type signatures, and any associated unit tests to run green. 

There is a PDF slidepack in each subdirectory containing:
- A statement of the problem
- How the exercise relates to broader principles covered in the workshop
- A Hints section with additional guidance & explanation, if required.

Every exercise step has a corresponding solution directory with a complete working solution. You can check your answer against the provided solution, or if you get stuck you can look at the solution. Attempting a problem with only partial success, and then studiying the solution, can stimulate learning quite effectively.

- [Step1 Decoding an Order](./step1/)   ( [step1 solution](./step1solution/) )  
- [Step2 Validating an Order](./step2/)   ( [step2 solution](./step2solution/) )  
- [Step3 Processing Unavailable Order](./step3/)   ( [step3 solution](./step3solution/) )  
- [Step4 Processing Available Orders](./step4/)   ( [step4 solution](./step4solution/) )  
- [Step5 Procesing an Order Sream](./step5/)   ( [step5 solution](./step5solution/) )  

## Getting Started

To do the hands on exercises you need existing familiarity with the Scala language and:

- JDK 8 or higher [installed](https://adoptopenjdk.net/installation.html)
- Scala Build Tool (SBT) [installed](http://www.scala-sbt.org/release/docs/Setup.html)
- An IDE such as [Intellij Community with Scala plugin](https://www.jetbrains.com/idea/download/) is recommended

The project uses a number of libraries from the [Typelevel functional ecosystem](https://typelevel.org/). They will be automatically
downloaded by SBT but the process can take from 5-30 minutes. Therefore, once SBT is installed, enter the following in a terminal to ensure all libraries download and the code works as expected:

```
> sbt
sbt> solution/test 
```
Expect to see the libraries download, then compilation, and finally tests tun green.

## Libraries Used

* [Cats](https://typelevel.org/cats/)
* [Cats Effect 3.x](https://typelevel.org/cats-effect/)
* [Circe](https://circe.github.io/circe/)
* [FS2 3.x](https://fs2.io/)
* [Refined](https://github.com/fthomas/refined)
* [Mouse](https://github.com/typelevel/mouse)
