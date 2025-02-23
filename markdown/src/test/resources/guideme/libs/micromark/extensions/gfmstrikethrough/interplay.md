# Interlpay

## Interleave with attention

a ~~two *emphasis* two~~ b

a ~~two **strong** two~~ b

a *marker ~~two marker* two~~ b

a ~~two *marker two~~ marker* b

## Interleave with links

a ~~two [resource](#) two~~ b

a ~~two [reference][#] two~~ b

a [label start ~~two label end](#) two~~ b

a ~~two [label start two~~ label end](#) b

a ~~two [label start ~one one~ label end](#) two~~ b

a ~one [label start ~~two two~~ label end](#) one~ b

a ~one [label start ~one one~ label end](#) one~ b

a ~~two [label start ~~two two~~ label end](#) two~~ b

[#]: #

## Interleave with code (text)

a ~~two `code` two~~ b

a ~~two `code two~~` b

a `code start ~~two code end` two~~ b

a ~~two `code start two~~ code end` b

a ~~two `code start ~one one~ code end` two~~ b

a ~one `code start ~~two two~~ code end` one~ b

a ~one `code start ~one one~ code end` one~ b

a ~~two `code start ~~two two~~ code end` two~~ b

## Emphasis/strong/strikethrough interplay

a ***~~xxx~~*** zzz

b ***xxx***zzz

c **xxx**zzz

d *xxx*zzz

e ***~~xxx~~***yyy

f **~~xxx~~**yyy

g *~~xxx~~*yyy

h ***~~xxx~~*** zzz

i **~~xxx~~** zzz

j *~~xxx~~* zzz

k ~~~**xxx**~~~ zzz

l ~~~xxx~~~zzz

m ~~xxx~~zzz

n ~xxx~zzz

o ~~~**xxx**~~~yyy

p ~~**xxx**~~yyy

r ~**xxx**~yyy

s ~~~**xxx**~~~ zzz

t ~~**xxx**~~ zzz

u ~**xxx**~ zzz
