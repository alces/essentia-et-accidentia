---
layout: post
title: 'Making a List Flat Using Python Generators'
date: 2015-11-30 14:53
comments: true
categories: [Python, generator, flatten]
---
![](http://uploads7.wikiart.org/images/gustave-dore/don-quixote-142.jpg)

In [the one of the previous posts](http://essentia-et-accidentia.logdown.com/posts/317025) in this blog, a function converting a nested list of lists to a flat list was mentioned. Just in order to recall, the idea was like that:

```
def flatten(aTree):
  add_next = lambda aList, anElem: \
    aList + flatten(anElem) if isinstance(anElem, list) \
      else aList + [anElem]
  return reduce(add_next, aTree, [])
```

This (quite naive) implementation actually works, does exactly what it's intended for, but has at least two obvious drawbacks:

* it can't work with a list of tuples, a tuple of lists, a set of sets, or other kinds of nested iterable objects;

* it works with the whole list at each step, so - when processing large lists - it may become pretty gready for resources.

In this article we're going to create a new implemetation of **flatten** function free from those drawbacks by using Python generators. If you've never heard about generators before, you could read, for example, [this introductory article](https://wiki.python.org/moin/Generators) from Python Wiki. A common pattern of writing programs using generators is that your function doesn't return a list (or an object of any other iterable type) as a whole, but yields its elements one by one (so **yeild** operator is used instead of **return**.) For example, consider this simplistic implementation of **xrange** function from the Python Standard Library:

```
def xrange(n):
  x = 0
  while x < n:
    yield x
    x += 1
```

Generators (when they're used with caution) are really great programming tool, but they can't help us with a problem of determining whether the next element of our list is something we should iterate over or not. Of course, it's possible to write something like this:

```
isinstance(anElem, list) or isinstance(anElem, tuple) or isinstance(anElem, set)
```

But this long list of similar calls gets me bored. The good news is that all the iterable objects in Python must have `__iter__` attribute, so the built-in function **hasattr** looking for this name always returns **True** for any iterable object and **False** for any non-iterable. The bad news is that strings in Python 3 also has `__iter__` attribute (although in Python 2 they haven't had.) Maybe, it looks not so stupidly to put it this way: 

```
hasattr(anElem, '__iter__') and not isinstance(anElem, str)
```

but in Python 2 unicode strings aren't the instances of **str** type. So, I think, the most natural way would be to call **hasattr** again - this time to search for an attribute that makes sense for string-like types but don't make any sense for other iterable types of data (I've choosen **split**, but you could either pick **isdigit**, **lower**, **rstrip**, or any other stuff you want.) Having known all that, we can write a generator producing a flat sequence from a nested one this way:

```
def flatten(aList):
	for anElem in aList:
		if hasattr(anElem, '__iter__') and not hasattr(anElem, 'split'):
			for subElem in flatten(anElem):
				yield subElem
		else:
			yield anElem
```

In order to attest that our newborn generator generate exactly what it should, let's add some asserts to our script (a la those in [the Groovy official documentation](http://groovy-lang.org/syntax.html)):

```
assert tuple(flatten([])) == ()
assert tuple(flatten(set([1, 2, 3]))) == (1, 2, 3)
assert tuple(flatten([[[0], 1, 2],
  (3, [], 4),
  [5, (6, 7)]])) == (0, 1, 2, 3, 4, 5, 6, 7)

assert tuple(flatten([['Doctor'],
  'Foster',
  ('went', [], 'to'),
  'Glocester'])) == ('Doctor', 'Foster', 'went', 'to', 'Glocester')

assert tuple(flatten(range(x)
  for x in range(10)))[10:20] == (0, 1, 2, 3, 4, 0, 1, 2, 3, 4)
```

Note that we have to wrap our function in a **tuple**, because - without this kind of conversion - it returns a sequence not a single object.

The last bad news for today is that Python knows nothing about end recursion (or other kinds of recursion's optimisation), so we could run into ugly `RuntimeError: maximum recursion depth exceeded` exception while trying to process too deeply nested lists by a function written that way.

----

Tested against `Python 2.7.8` and `Python 3.4.3`