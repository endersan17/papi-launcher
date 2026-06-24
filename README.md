Original README.md: https://github.com/HMCL-dev/HMCL/blob/main/README.md

The same HCML but with allowed offline accounts by default without having to register with Microsoft first.

I only changed one file: "(ROOT folder)\HMCL\src\main\java\org\jackhuang\hmcl\ui\account\AccountListPage.java"
This was the only file that needed to be changed to unlock the Offline option.
All the logic of the restrictions was concentrated in this file:
A static block with RESTRICTED definition logic, UI code with conditional display and locking of elements
I haven't touched any other files. 
This is a minimal change that solves the problem - now the Offline option is always available, without having to log into a Microsoft account first.

Comparison of differences:
https://github.com/HMCL-dev/HMCL/commit/32b7a60d2ae18c8c416db3ee7d8f8e74eed72205

(There is no guarantee that this branch will be updated in the future).
